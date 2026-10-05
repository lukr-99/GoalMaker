using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Wants page (docs/wants.md, M8-04): a Wants and Needs switch on top, which this PC remembers.
/// Under Wants: the thresholds, filters for Ready, Cooling and Decided, a row per want with its ring,
/// the add and edit panel with the cooldown a price gives, and the thresholds panel. Under Needs: the
/// open needs by the day they are needed by, and the bought and dropped ones folded below. Deciding
/// and deleting offer undo for five seconds.
/// </summary>
public sealed partial class WantsViewModel : ObservableObject
{
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private readonly WantList wants;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<Action> runOnUi;
    private WantState? chosen;
    private string? editingId;
    private Action? undo;
    private ITimer? undoTimer;
    private Action? onAdded;
    private string draftKind = WantRules.Want;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsWantsTab), nameof(IsNeedsTab))]
    private WantsTab tab;

    [ObservableProperty]
    private WantState filter = WantState.Ready;

    [ObservableProperty]
    private string readyLabel = string.Empty;

    [ObservableProperty]
    private string coolingLabel = string.Empty;

    [ObservableProperty]
    private string decidedLabel = string.Empty;

    [ObservableProperty]
    private string emptyText = string.Empty;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string cooldownsLine = string.Empty;

    // The Needs tab.
    [ObservableProperty]
    private string needsLabel = string.Empty;

    [ObservableProperty]
    private bool hasNoOpenNeeds;

    [ObservableProperty]
    private string needsEmptyText = string.Empty;

    [ObservableProperty]
    private bool hasNeedsDone;

    [ObservableProperty]
    private string needsDoneLabel = string.Empty;

    [ObservableProperty]
    private bool isNeedsDoneOpen;

    [ObservableProperty]
    private bool hasUndo;

    [ObservableProperty]
    private string undoText = string.Empty;

    // The add and edit panel.
    [ObservableProperty]
    private bool isEditing;

    [ObservableProperty]
    private bool isAdding;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveDraftCommand))]
    private string draftTitle = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveDraftCommand))]
    private string draftReason = string.Empty;

    [ObservableProperty]
    private string draftPrice = string.Empty;

    [ObservableProperty]
    private string draftCurrency = "CZK";

    [ObservableProperty]
    private string draftLink = string.Empty;

    [ObservableProperty]
    private string draftDays = string.Empty;

    [ObservableProperty]
    private DateTime? draftNeedBy;

    private int? pickedDays;

    // The thresholds panel.
    [ObservableProperty]
    private bool isEditingCooldowns;

    [ObservableProperty]
    private string smallUnder = string.Empty;

    [ObservableProperty]
    private string smallDays = string.Empty;

    [ObservableProperty]
    private string mediumUnder = string.Empty;

    [ObservableProperty]
    private string mediumDays = string.Empty;

    [ObservableProperty]
    private string largeDays = string.Empty;

    [ObservableProperty]
    private string unpricedDays = string.Empty;

    [ObservableProperty]
    private string cooldownsCurrency = string.Empty;

    [ObservableProperty]
    private bool cooldownsRefused;

    /// <param name="chat">The quick chat the bottom bar switches to; null where there is none.</param>
    public WantsViewModel(WantList wants, ISettingsStore settings, IStrings strings, TimeProvider time, Action<Action> runOnUi, ChatViewModel? chat = null)
    {
        Bar = new WantBarViewModel(wants, strings, StartAdding, chat);
        this.wants = wants;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.runOnUi = runOnUi;
        Tab = settings.WantsTab;
        Bar.ForNeeds = Tab == WantsTab.Needs;
        wants.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public ObservableCollection<WantRowViewModel> Rows { get; } = [];

    /// <summary>The open needs, by the day they are needed by (none last), then when they were added.</summary>
    public ObservableCollection<WantRowViewModel> NeedRows { get; } = [];

    /// <summary>The bought and dropped needs, folded below the open ones, the last decided first.</summary>
    public ObservableCollection<WantRowViewModel> NeedsDoneRows { get; } = [];

    /// <summary>The wants that wait out a cooldown show; the switch's Wants choice.</summary>
    public bool IsWantsTab
    {
        get => Tab == WantsTab.Wants;
        set
        {
            if (value)
            {
                Tab = WantsTab.Wants;
            }
        }
    }

    /// <summary>The needs to buy show; the switch's Needs choice.</summary>
    public bool IsNeedsTab
    {
        get => Tab == WantsTab.Needs;
        set
        {
            if (value)
            {
                Tab = WantsTab.Needs;
            }
        }
    }

    /// <summary>Whether the add and edit panel holds a need: no cooldown, a day it is needed by, and the reason optional.</summary>
    public bool IsDraftNeed => draftKind == WantRules.Need;

    /// <summary>The cooldown a new want gets shows in the panel; a need has none.</summary>
    public bool ShowsDraftDays => IsAdding && !IsDraftNeed;

    /// <summary>The bottom bar: type a want to add it, or open the add panel with its plus.</summary>
    public WantBarViewModel Bar { get; }

    public bool ShowsReady => Filter == WantState.Ready;

    public bool ShowsCooling => Filter == WantState.Cooling;

    public bool ShowsDecided => Filter == WantState.Decided;

    public string PanelTitle => strings.Get((IsAdding, IsDraftNeed) switch
    {
        (true, false) => "Wants.Add",
        (false, false) => "Wants.Edit",
        (true, true) => "Wants.AddNeed",
        (false, true) => "Wants.EditNeed",
    });

    /// <summary>What the reason field is called: why it is wanted, or for a need an optional note.</summary>
    public string ReasonLabel => strings.Get(IsDraftNeed ? "Wants.NeedNoteField" : "Wants.WhyField");

    public string ReasonHint => strings.Get(IsDraftNeed ? "Wants.NeedNoteHint" : "Wants.WhyHint");

    public void Refresh()
    {
        var today = Today();
        var everything = wants.All();
        var all = everything
            .Where(want => want.Kind != WantRules.Need)
            .Select(want => (Want: want, State: WantRules.State(want, today)))
            .Where(pair => pair.State is not null)
            .ToList();
        int Count(WantState state) => all.Count(pair => pair.State == state);
        ReadyLabel = Label("Wants.Ready", Count(WantState.Ready));
        CoolingLabel = Label("Wants.Cooling", Count(WantState.Cooling));
        DecidedLabel = Label("Wants.Decided", Count(WantState.Decided));
        Filter = chosen ?? (Count(WantState.Ready) > 0 ? WantState.Ready : WantState.Cooling);

        var shown = all.Where(pair => pair.State == Filter).ToList();
        if (Filter == WantState.Decided)
        {
            shown = [.. shown.OrderByDescending(pair => pair.Want.DecidedAt, StringComparer.Ordinal)];
        }

        Rows.Clear();
        foreach (var (want, state) in shown)
        {
            var daysLeft = Math.Max(0, want.CoolsUntil.DayNumber - today.DayNumber);
            var status = state switch
            {
                WantState.Ready => strings.Get("Wants.StatusReady"),
                WantState.Cooling => strings.Get(daysLeft == 1 ? "Wants.StatusDay" : "Wants.StatusDays", daysLeft),
                _ => strings.Get(want.Decision == WantRules.Bought ? "Wants.StatusBought" : "Wants.StatusDropped"),
            };
            var price = want.Price is { } amount ? Money(amount, want.Currency) : null;
            var maker = want.MadeBy == ProjectRules.Claude ? strings.Get("Wants.ByClaude") : null;
            var checkedText = want.CheckedPrice is { } found
                ? string.Join(" · ", new[] { Money(found, want.Currency), want.CheckedNote }.Where(part => part.Length > 0))
                : null;
            Rows.Add(new WantRowViewModel(
                this, want, state!.Value, WantRules.Progress(want, today), daysLeft,
                string.Join(" · ", new[] { price, status, maker }.Where(part => part is not null)), checkedText));
        }

        IsEmpty = Rows.Count == 0;
        EmptyText = strings.Get(all.Count == 0 ? "Wants.Empty" : Filter switch
        {
            WantState.Ready => "Wants.NoneReady",
            WantState.Cooling => "Wants.NoneCooling",
            _ => "Wants.NoneDecided",
        });

        RefreshNeeds(everything, today);

        var c = wants.Cooldowns();
        CooldownsLine = strings.Get(
            "Wants.CooldownsLine", Money(c.SmallUnder, c.Currency), c.SmallDays, Money(c.MediumUnder, c.Currency), c.MediumDays, c.LargeDays, c.UnpricedDays);
        UpdateDraftDays();
    }

    /// <summary>Opens the add panel with <paramref name="title"/> filled in (from <c>/want</c> in a composer), on the Wants tab.</summary>
    public void StartAdding(string title)
    {
        Tab = WantsTab.Wants;
        StartAdding(new WantLine(title.Trim(), null, null, null, null), null);
    }

    /// <summary>
    /// Opens the add panel filled in with what the bottom bar read (null: blank); <paramref name="added"/>
    /// runs once the want is saved, so the bar can let go of its line.
    /// </summary>
    public void StartAdding(WantLine? line, Action? added)
    {
        editingId = null;
        onAdded = added;
        IsAdding = true;
        SetDraftKind(Tab == WantsTab.Needs ? WantRules.Need : WantRules.Want);
        DraftNeedBy = null;
        DraftTitle = line?.Title ?? string.Empty;
        DraftReason = line?.Reason ?? string.Empty;
        DraftPrice = line?.Price is { } price ? price.ToString(CultureInfo.CurrentCulture) : string.Empty;
        DraftCurrency = line?.Currency ?? wants.Cooldowns().Currency;
        DraftLink = string.Empty;
        pickedDays = line?.WaitDays;
        UpdateDraftDays();
        OnPropertyChanged(nameof(PanelTitle));
        IsEditing = true;
    }

    public void StartEdit(WantRowViewModel row)
    {
        editingId = row.Want.Id;
        onAdded = null;
        IsAdding = false;
        SetDraftKind(row.Want.Kind);
        DraftNeedBy = row.Want.NeedBy?.ToDateTime(TimeOnly.MinValue);
        DraftTitle = row.Want.Title;
        DraftReason = row.Want.Reason;
        DraftPrice = row.Want.Price is { } price ? price.ToString(CultureInfo.CurrentCulture) : string.Empty;
        DraftCurrency = row.Want.Currency;
        DraftLink = row.Want.Link ?? string.Empty;
        OnPropertyChanged(nameof(PanelTitle));
        IsEditing = true;
    }

    public void Decide(WantRowViewModel row, string decision, string note)
    {
        if (wants.Decide(row.Want.Id, decision, note))
        {
            ShowUndo(strings.Get(decision == WantRules.Bought ? "Wants.BoughtMessage" : "Wants.DroppedMessage", row.Title), () => wants.Reopen(row.Want.Id));
        }
    }

    public void Reopen(WantRowViewModel row) => wants.Reopen(row.Want.Id);

    public void Delete(WantRowViewModel row)
    {
        if (wants.Delete(row.Want.Id))
        {
            ShowUndo(strings.Get("Wants.DeletedMessage", row.Title), () => wants.Restore(row.Want.Id));
        }
    }

    [RelayCommand]
    private void Show(WantState state)
    {
        chosen = state;
        Refresh();
    }

    [RelayCommand]
    private void Add() => StartAdding(null, null);

    [RelayCommand]
    private void ToggleNeedsDone() => IsNeedsDoneOpen = !IsNeedsDoneOpen;

    // A want says why it is wanted; a need may leave it out.
    private bool CanSaveDraft() => DraftTitle.Trim().Length > 0 && (IsDraftNeed || DraftReason.Trim().Length > 0);

    [RelayCommand(CanExecute = nameof(CanSaveDraft))]
    private void SaveDraft()
    {
        var draft = IsDraftNeed
            ? new WantDraft(
                DraftTitle, DraftReason, DraftLink, ParseMoney(DraftPrice), DraftCurrency,
                Kind: WantRules.Need, NeedBy: DraftNeedBy is { } day ? DateOnly.FromDateTime(day) : null)
            : new WantDraft(DraftTitle, DraftReason, DraftLink, ParseMoney(DraftPrice), DraftCurrency, PickedDays: pickedDays);
        var saved = editingId is null ? wants.Add(draft) is not null : wants.Update(editingId, draft);
        if (saved)
        {
            IsEditing = false;
            if (editingId is null)
            {
                onAdded?.Invoke();
            }

            onAdded = null;
        }
    }

    [RelayCommand]
    private void CancelDraft()
    {
        onAdded = null;
        IsEditing = false;
    }

    [RelayCommand]
    private void MoreDays() => Pick(+1);

    [RelayCommand]
    private void FewerDays() => Pick(-1);

    [RelayCommand]
    private void EditCooldowns()
    {
        var c = wants.Cooldowns();
        FillCooldowns(c);
        CooldownsRefused = false;
        IsEditingCooldowns = true;
    }

    [RelayCommand]
    private void ResetCooldowns() => FillCooldowns(WantCooldowns.Default);

    [RelayCommand]
    private void SaveCooldowns()
    {
        var value = ParseMoney(SmallUnder) is { } small && ParseMoney(MediumUnder) is { } medium
            && int.TryParse(SmallDays, out var smallWait) && int.TryParse(MediumDays, out var mediumWait)
            && int.TryParse(LargeDays, out var largeWait) && int.TryParse(UnpricedDays, out var unpricedWait)
            ? new WantCooldowns(small, smallWait, medium, mediumWait, largeWait, unpricedWait, CooldownsCurrency)
            : null;
        if (value is not null && wants.SetCooldowns(value))
        {
            IsEditingCooldowns = false;
            return;
        }

        CooldownsRefused = true;
    }

    [RelayCommand]
    private void CancelCooldowns() => IsEditingCooldowns = false;

    [RelayCommand]
    private void Undo()
    {
        var action = undo;
        HideUndo();
        action?.Invoke();
    }

    partial void OnTabChanged(WantsTab value)
    {
        if (settings.WantsTab != value)
        {
            settings.WantsTab = value;
        }

        // A panel opened for the other tab would add the wrong kind, so it closes.
        onAdded = null;
        IsEditing = false;
        IsEditingCooldowns = false;
        Bar.ForNeeds = value == WantsTab.Needs;
    }

    partial void OnIsAddingChanged(bool value) => OnPropertyChanged(nameof(ShowsDraftDays));

    partial void OnFilterChanged(WantState value)
    {
        OnPropertyChanged(nameof(ShowsReady));
        OnPropertyChanged(nameof(ShowsCooling));
        OnPropertyChanged(nameof(ShowsDecided));
    }

    partial void OnDraftPriceChanged(string value) => UpdateDraftDays();

    partial void OnDraftCurrencyChanged(string value) => UpdateDraftDays();

    private void SetDraftKind(string kind)
    {
        draftKind = kind == WantRules.Need ? WantRules.Need : WantRules.Want;
        OnPropertyChanged(nameof(IsDraftNeed));
        OnPropertyChanged(nameof(ShowsDraftDays));
        OnPropertyChanged(nameof(ReasonLabel));
        OnPropertyChanged(nameof(ReasonHint));
        SaveDraftCommand.NotifyCanExecuteChanged();
    }

    private void RefreshNeeds(IReadOnlyList<WantItem> everything, DateOnly today)
    {
        var open = WantRules.Needs(everything);
        var done = everything
            .Where(want => want.Kind == WantRules.Need && want.Decision is not null)
            .OrderByDescending(want => want.DecidedAt, StringComparer.Ordinal)
            .ToList();

        NeedRows.Clear();
        foreach (var need in open)
        {
            NeedRows.Add(NeedRow(need, today));
        }

        NeedsDoneRows.Clear();
        foreach (var need in done)
        {
            NeedsDoneRows.Add(NeedRow(need, today));
        }

        NeedsLabel = Label("Wants.Needs", open.Count);
        HasNoOpenNeeds = open.Count == 0;
        NeedsEmptyText = strings.Get(done.Count == 0 ? "Wants.NeedsEmpty" : "Wants.NoNeedsOpen");
        HasNeedsDone = done.Count > 0;
        NeedsDoneLabel = strings.Get("Wants.NeedsDone", done.Count);
    }

    // The price, the day it is needed by, then by Claude, and bought or dropped once decided.
    private WantRowViewModel NeedRow(WantItem need, DateOnly today)
    {
        const string Separator = " · ";
        var price = need.Price is { } amount ? Money(amount, need.Currency) : null;
        var due = need.NeedBy is { } day
            ? strings.Get("Wants.NeedBy", day.ToString(day.Year == today.Year ? "ddd d MMM" : "ddd d MMM yyyy", CultureInfo.CurrentCulture))
            : null;
        var after = new List<string>();
        if (need.MadeBy == ProjectRules.Claude)
        {
            after.Add(strings.Get("Wants.ByClaude"));
        }

        if (need.Decision is { } decision)
        {
            after.Add(strings.Get(decision == WantRules.Bought ? "Wants.StatusBought" : "Wants.StatusDropped"));
        }

        var afterText = string.Join(Separator, after);
        var line = new NeedLine(
            price is null ? string.Empty : price + (due is not null || afterText.Length > 0 ? Separator : string.Empty),
            due ?? string.Empty,
            afterText.Length > 0 && due is not null ? Separator + afterText : afterText,
            WantRules.NeedLate(need, today));
        var subtitle = string.Join(Separator, new[] { price, due }.Concat(after).Where(part => part is not null));
        var state = WantRules.State(need, today) ?? WantState.Ready;
        return new WantRowViewModel(this, need, state, 1, 0, subtitle, null, line);
    }

    private void Pick(int step)
    {
        var current = Days();
        pickedDays = Math.Clamp(current + step, 0, WantRules.MaxDays);
        UpdateDraftDays();
    }

    private int Days() => WantRules.CooldownDays(ParseMoney(DraftPrice), DraftCurrency.Trim().ToUpperInvariant(), wants.Cooldowns(), pickedDays);

    private void UpdateDraftDays()
    {
        var days = Days();
        DraftDays = strings.Get(days == 1 ? "Wants.Day" : "Wants.Days", days);
    }

    private void FillCooldowns(WantCooldowns c)
    {
        SmallUnder = c.SmallUnder.ToString(CultureInfo.CurrentCulture);
        SmallDays = c.SmallDays.ToString(CultureInfo.CurrentCulture);
        MediumUnder = c.MediumUnder.ToString(CultureInfo.CurrentCulture);
        MediumDays = c.MediumDays.ToString(CultureInfo.CurrentCulture);
        LargeDays = c.LargeDays.ToString(CultureInfo.CurrentCulture);
        UnpricedDays = c.UnpricedDays.ToString(CultureInfo.CurrentCulture);
        CooldownsCurrency = c.Currency;
    }

    private void ShowUndo(string text, Action action)
    {
        undoTimer?.Dispose();
        undo = action;
        UndoText = text;
        HasUndo = true;
        undoTimer = time.CreateTimer(_ => runOnUi(HideUndo), null, UndoFor, Timeout.InfiniteTimeSpan);
    }

    private void HideUndo()
    {
        undoTimer?.Dispose();
        undoTimer = null;
        undo = null;
        HasUndo = false;
    }

    private string Label(string key, int count) =>
        count > 0 ? strings.Get(key) + " " + count.ToString(CultureInfo.CurrentCulture) : strings.Get(key);

    internal static string Money(double amount, string currency) =>
        amount.ToString(amount % 1 == 0 ? "N0" : "N2", CultureInfo.CurrentCulture) + " " + currency;

    /// <summary>A price typed with a comma or a point; null for nothing or something that isn't one.</summary>
    internal static double? ParseMoney(string text)
    {
        var clean = text.Replace(" ", string.Empty, StringComparison.Ordinal).Replace(" ", string.Empty, StringComparison.Ordinal).Replace(',', '.').Trim();
        return clean.Length > 0 && double.TryParse(clean, NumberStyles.Float, CultureInfo.InvariantCulture, out var value) && value >= 0 ? value : null;
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}

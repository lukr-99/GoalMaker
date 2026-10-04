using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Life goals page (docs/life-goals.md, M9-03): a card per open life goal in the owner's order with
/// its pictures and time left, the achieved and dropped ones folded below, and the editor. Achieving,
/// dropping and deleting offer undo for five seconds; deleting asks first.
/// </summary>
public sealed partial class LifeGoalsViewModel : ObservableObject
{
    private const string Dot = " · ";
    private const int CardWidth = 900;
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private readonly LifeGoalList lifeGoals;
    private readonly LifeGoalPictures pictures;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<Action> runOnUi;
    private readonly Dictionary<string, int> shownPictures = new(StringComparer.Ordinal);
    private Action? undo;
    private ITimer? undoTimer;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private bool hasClosed;

    [ObservableProperty]
    private string closedLabel = string.Empty;

    [ObservableProperty]
    private bool showsClosed;

    [ObservableProperty]
    private bool hasUndo;

    [ObservableProperty]
    private string undoText = string.Empty;

    /// <summary>The life goal Delete asks about, or null while nothing is asked.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsConfirmingDelete), nameof(DeleteQuestion))]
    private LifeGoalCardViewModel? deleting;

    public LifeGoalsViewModel(
        LifeGoalList lifeGoals,
        LifeGoalPictures pictures,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Action<Action> runOnUi,
        Func<string, ShrunkPicture?> shrink,
        Func<IReadOnlyList<string>> pickPictures)
    {
        this.lifeGoals = lifeGoals;
        this.pictures = pictures;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.runOnUi = runOnUi;
        Editor = new LifeGoalEditorViewModel(lifeGoals, pictures, strings, Today, shrink, pickPictures);
        lifeGoals.Changed += (_, _) => runOnUi(Refresh);
        pictures.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>The open life goals in the owner's order.</summary>
    public ObservableCollection<LifeGoalCardViewModel> Open { get; } = [];

    /// <summary>The achieved and dropped ones, the most recent first.</summary>
    public ObservableCollection<LifeGoalCardViewModel> Closed { get; } = [];

    public LifeGoalEditorViewModel Editor { get; }

    public bool IsConfirmingDelete => Deleting is not null;

    public string DeleteQuestion => Deleting is { } card ? strings.Get("LifeGoals.DeleteQuestion", card.Title) : string.Empty;

    /// <summary>The planning day the by dates count from.</summary>
    public DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    public void Refresh()
    {
        var today = Today();
        var byGoal = lifeGoals.AllPictures().ToLookup(picture => picture.LifeGoalId, StringComparer.Ordinal);
        var all = lifeGoals.All();
        var open = all.Where(goal => goal.Status == LifeGoalRules.Open).ToList();
        var closed = all.Where(goal => goal.Status != LifeGoalRules.Open).ToList();

        Open.Clear();
        for (var index = 0; index < open.Count; index++)
        {
            Open.Add(Card(open[index], byGoal[open[index].Id], today, index > 0, index < open.Count - 1));
        }

        Closed.Clear();
        foreach (var goal in closed)
        {
            Closed.Add(Card(goal, byGoal[goal.Id], today, false, false));
        }

        IsEmpty = all.Count == 0;
        HasClosed = closed.Count > 0;
        ClosedLabel = strings.Get("LifeGoals.Closed", closed.Count);
    }

    /// <summary>A life goal's time left in words: "10 years left", "8 months left", "Today", "Past its date".</summary>
    public static string TimeLeftText(TimeLeft left, IStrings strings) => left.Unit switch
    {
        TimeLeftUnit.Years => strings.Get(left.Count == 1 ? "LifeGoals.YearLeft" : "LifeGoals.YearsLeft", left.Count),
        TimeLeftUnit.Months => strings.Get(left.Count == 1 ? "LifeGoals.MonthLeft" : "LifeGoals.MonthsLeft", left.Count),
        TimeLeftUnit.Days => strings.Get(left.Count == 1 ? "LifeGoals.DayLeft" : "LifeGoals.DaysLeft", left.Count),
        TimeLeftUnit.Today => strings.Get("LifeGoals.Today"),
        _ => strings.Get("LifeGoals.Past"),
    };

    /// <summary>The life goal the page should bring into view and give the keyboard, or null for none.</summary>
    public string? FocusRequest { get; private set; }

    /// <summary>Raised when <see cref="FocusRequest"/> is set, so a page on screen moves to that card.</summary>
    public event EventHandler? FocusRequested;

    /// <summary>Asks the page to show the card of <paramref name="lifeGoalId"/>, as the why reminder's toast does.</summary>
    public void Focus(string lifeGoalId)
    {
        FocusRequest = lifeGoalId;
        FocusRequested?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>The card the page should move to, taken once: open ones first, then a closed one, which unfolds them.</summary>
    public LifeGoalCardViewModel? TakeFocusRequest()
    {
        if (FocusRequest is not { } id)
        {
            return null;
        }

        FocusRequest = null;
        if (Open.FirstOrDefault(card => card.Goal.Id == id) is { } open)
        {
            return open;
        }

        var closed = Closed.FirstOrDefault(card => card.Goal.Id == id);
        if (closed is not null)
        {
            ShowsClosed = true;
        }

        return closed;
    }

    internal void Edit(LifeGoalCardViewModel card) =>
        Editor.Open(card.Goal, lifeGoals.PicturesOf(card.Goal.Id));

    internal void Achieve(LifeGoalCardViewModel card)
    {
        if (lifeGoals.Achieve(card.Goal.Id))
        {
            ShowUndo(strings.Get("LifeGoals.AchievedMessage", card.Title), () => lifeGoals.Reopen(card.Goal.Id));
        }
    }

    internal void Drop(LifeGoalCardViewModel card)
    {
        if (lifeGoals.Drop(card.Goal.Id))
        {
            ShowUndo(strings.Get("LifeGoals.DroppedMessage", card.Title), () => lifeGoals.Reopen(card.Goal.Id));
        }
    }

    internal void Reopen(LifeGoalCardViewModel card) => lifeGoals.Reopen(card.Goal.Id);

    /// <summary>Moves an open life goal one place up (<paramref name="by"/> -1) or down (1).</summary>
    internal void Move(LifeGoalCardViewModel card, int by)
    {
        var ids = Open.Select(open => open.Goal.Id).ToList();
        var from = ids.IndexOf(card.Goal.Id);
        var to = from + by;
        if (from < 0 || to < 0 || to >= ids.Count)
        {
            return;
        }

        ids.RemoveAt(from);
        ids.Insert(to, card.Goal.Id);
        lifeGoals.Reorder(ids);
    }

    internal void AskDelete(LifeGoalCardViewModel card) => Deleting = card;

    [RelayCommand]
    private void Add() => Editor.Open(null, []);

    [RelayCommand]
    private void ToggleClosed() => ShowsClosed = !ShowsClosed;

    [RelayCommand]
    private void ConfirmDelete()
    {
        if (Deleting is not { } card)
        {
            return;
        }

        Deleting = null;
        if (lifeGoals.Delete(card.Goal.Id))
        {
            ShowUndo(strings.Get("LifeGoals.DeletedMessage", card.Title), () => lifeGoals.Restore(card.Goal.Id));
        }
    }

    [RelayCommand]
    private void CancelDelete() => Deleting = null;

    [RelayCommand]
    private void Undo()
    {
        var action = undo;
        HideUndo();
        action?.Invoke();
    }

    private LifeGoalCardViewModel Card(LifeGoalItem goal, IEnumerable<LifeGoalPicture> own, DateOnly today, bool canMoveUp, bool canMoveDown)
    {
        var status = goal.Status switch
        {
            LifeGoalRules.Achieved => strings.Get("LifeGoals.StatusAchieved"),
            LifeGoalRules.Dropped => strings.Get("LifeGoals.StatusDropped"),
            _ => LifeGoalRules.TimeLeft(goal.By, today) is { } left ? TimeLeftText(left, strings) : null,
        };
        var maker = goal.MadeBy == ProjectRules.Claude ? strings.Get("LifeGoals.ByClaude") : null;
        var list = own.ToList();
        var shown = list.Select((picture, index) => new LifeGoalPictureViewModel(
            picture.Id,
            null,
            strings.Get("LifeGoals.Picture", index + 1, list.Count, goal.Title),
            () => pictures.Read(picture.Id),
            CardWidth)).ToList();
        return new LifeGoalCardViewModel(
            this,
            goal,
            string.Join(Dot, new[] { status, maker }.OfType<string>()),
            shown,
            canMoveUp,
            canMoveDown,
            strings.Get("LifeGoals.Menu", goal.Title),
            shownPictures.GetValueOrDefault(goal.Id),
            (id, index) => shownPictures[id] = index);
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
}

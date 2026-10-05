using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Tally page (docs/tally.md, M8-13, stories 109 to 113): the switch on top with what is recorded
/// and what syncs, filter chips (Phone, PC, a category), the day (today, or the day picked in the week)
/// as one stacked bar by category, then that day by hour and the apps, sites and folders on this PC
/// (from its own log, which never syncs) with what landed in Other to sort first, the week as stacked
/// bars per day, the week on each device, the last eight weeks, time per project, and the owner's rules
/// and categories with a panel each to add, edit and merge them. Every device's time counts in the
/// bars; the chips narrow everything. Move to under an app, a site or a folder sorts it in one step;
/// Make a rule fills the rule panel in.
/// </summary>
public sealed partial class TallyViewModel : ObservableObject
{
    /// <summary>How many weeks the Last 8 weeks card shows, the one holding today last.</summary>
    public const int RecentWeeks = 8;

    private readonly TallyList tally;
    private readonly TallyDefaults defaults;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Action<bool>? switchTally;
    private readonly Func<DateOnly, DateOnly, IReadOnlyList<TallyStretch>>? stretches;
    private readonly Action? recount;
    private readonly HashSet<string> open = new(StringComparer.Ordinal);
    private TallyLabels labels;
    private string? kind;
    private string? category;
    private DateOnly? pickedDay;
    private string? editingRuleId;
    private string? editingCategoryId;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoTime))]
    private bool hasTime;

    [ObservableProperty]
    private string dayTitle = string.Empty;

    [ObservableProperty]
    private string dayTotal = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoDay))]
    private bool hasDay;

    [ObservableProperty]
    private string dayEmpty = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsNotToday))]
    private bool isToday = true;

    [ObservableProperty]
    private IReadOnlyList<(double Amount, Brush? Brush)> dayParts = [];

    // This PC's own look at the day: its hours and its apps.
    [ObservableProperty]
    private bool showLocal;

    [ObservableProperty]
    private bool showLocalElsewhere;

    [ObservableProperty]
    private string hoursDescription = string.Empty;

    /// <summary>The time under every sixth hour of the day chart: "04:00", "10:00", "16:00", "22:00".</summary>
    [ObservableProperty]
    private IReadOnlyList<string> hourMarks = [];

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(AppsForDay))]
    private bool appsForWeek;

    [ObservableProperty]
    private string dayChoice = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoApps))]
    private bool hasApps;

    // What landed in Other on this PC, to sort in one step.
    [ObservableProperty]
    private bool showToSort;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNothingToSort))]
    private bool hasToSort;

    // The last eight weeks, and this week on each device.
    [ObservableProperty]
    private bool hasRecentWeeks;

    [ObservableProperty]
    private string recentTotal = string.Empty;

    [ObservableProperty]
    private string recentFirstWeek = string.Empty;

    [ObservableProperty]
    private string weekTotal = string.Empty;

    [ObservableProperty]
    private bool hasProjects;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoRules))]
    private bool hasRules;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoCategories))]
    private bool hasCategories;

    [ObservableProperty]
    private IReadOnlyList<FilterChoiceViewModel> categoryChoices = [];

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> projectChoices = [];

    // The rule panel.
    [ObservableProperty]
    private bool isEditingRule;

    [ObservableProperty]
    private bool isAddingRule;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PatternHint))]
    private ChoiceViewModel draftMatch;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveRuleCommand))]
    private string draftPattern = string.Empty;

    [ObservableProperty]
    private ChoiceViewModel draftPlatform;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveRuleCommand))]
    private FilterChoiceViewModel? draftCategory;

    [ObservableProperty]
    private ChoiceViewModel? draftProject;

    [ObservableProperty]
    private bool ruleRefused;

    // The category panel.
    [ObservableProperty]
    private bool isEditingCategory;

    [ObservableProperty]
    private bool isAddingCategory;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCategoryCommand))]
    private string draftName = string.Empty;

    [ObservableProperty]
    private ColorOptionViewModel? draftColor;

    [ObservableProperty]
    private string draftEmoji = string.Empty;

    // Merging the category being edited into another one, after asking.
    [ObservableProperty]
    private bool canMerge;

    [ObservableProperty]
    private IReadOnlyList<FilterChoiceViewModel> mergeChoices = [];

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AskMergeCommand))]
    private FilterChoiceViewModel? mergeTarget;

    [ObservableProperty]
    private bool isAskingMerge;

    [ObservableProperty]
    private string mergeQuestion = string.Empty;

    // The categories to sort into: the owner's own first, then the shipped ones.
    private IReadOnlyList<FilterChoiceViewModel> sortChoices = [];

    public TallyViewModel(
        TallyList tally,
        TallyDefaults defaults,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        IReadOnlyList<string> palette,
        Action<Action> runOnUi,
        Action<bool>? switchTally = null,
        Func<DateOnly, DateOnly, IReadOnlyList<TallyStretch>>? stretches = null,
        Action? recount = null)
    {
        this.tally = tally;
        this.defaults = defaults;
        this.projects = projects;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.switchTally = switchTally;
        this.stretches = stretches;
        this.recount = recount;
        labels = new TallyLabels(defaults, [], strings, areaBrush);
        MatchChoices =
        [
            new(TallyRules.App, strings.Get("Tally.MatchApp")),
            new(TallyRules.Title, strings.Get("Tally.MatchTitle")),
            new(TallyRules.Folder, strings.Get("Tally.MatchFolder")),
        ];
        PlatformChoices =
        [
            new(TallyRules.Any, strings.Get("Tally.OnBoth")),
            new(TallyRules.Windows, strings.Get("Tally.OnPc")),
            new(TallyRules.Android, strings.Get("Tally.OnPhone")),
        ];
        Colors = [.. palette.Select(id => new ColorOptionViewModel(id, CultureInfo.CurrentCulture.TextInfo.ToTitleCase(id.Replace('-', ' ')), areaBrush(id)))];
        draftMatch = MatchChoices[0];
        draftPlatform = PlatformChoices[0];
        tally.Changed += (_, _) => runOnUi(Refresh);
        projects.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>Phone, PC, then each category with time this week; a chosen chip narrows everything below it.</summary>
    public ObservableCollection<FilterOptionViewModel> Chips { get; } = [];

    public ObservableCollection<TallySegmentViewModel> DayLegend { get; } = [];

    /// <summary>The shown day's 24 hours on this PC, from the hour the planning day starts.</summary>
    public ObservableCollection<TallyHourViewModel> Hours { get; } = [];

    /// <summary>This PC's apps by category, for the shown day or the week.</summary>
    public ObservableCollection<TallyAppGroupViewModel> Apps { get; } = [];

    /// <summary>What landed in Other on this PC (apps, sites and folders), most first, to sort in one step.</summary>
    public ObservableCollection<TallySortRowViewModel> ToSort { get; } = [];

    /// <summary>Monday to Sunday of this planning week.</summary>
    public ObservableCollection<TallyBarViewModel> WeekDays { get; } = [];

    /// <summary>The phone and the PC this week, each one bar by category, from the synced days.</summary>
    public ObservableCollection<TallyBarViewModel> Devices { get; } = [];

    /// <summary>The last eight weeks, oldest first, each stacked by category.</summary>
    public ObservableCollection<TallyBarViewModel> RecentWeekBars { get; } = [];

    /// <summary>The categories over the last eight weeks, most first.</summary>
    public ObservableCollection<TallySegmentViewModel> RecentLegend { get; } = [];

    public ObservableCollection<TallyProjectViewModel> Projects { get; } = [];

    public ObservableCollection<TallyRuleRowViewModel> Rules { get; } = [];

    public ObservableCollection<TallyCategoryRowViewModel> Categories { get; } = [];

    public IReadOnlyList<ChoiceViewModel> MatchChoices { get; }

    public IReadOnlyList<ChoiceViewModel> PlatformChoices { get; }

    public IReadOnlyList<ColorOptionViewModel> Colors { get; }

    public bool HasNoTime => !HasTime;

    public bool HasNoDay => !HasDay;

    public bool HasNoApps => !HasApps;

    public bool HasNothingToSort => !HasToSort;

    public bool IsNotToday => !IsToday;

    public bool AppsForDay => !AppsForWeek;

    public bool HasNoRules => !HasRules;

    public bool HasNoCategories => !HasCategories;

    /// <summary>The device kind the page is narrowed to (phone or pc), or null for both.</summary>
    public string? Kind => kind;

    /// <summary>The category the page is narrowed to, or null for all.</summary>
    public string? Category => category;

    /// <summary>
    /// Whether Tally follows the window in front on this PC (stories 109 to 111). Off until the owner
    /// turns it on; the tracker starts and stops with it.
    /// </summary>
    public bool TallyOn
    {
        get => settings.TallyOn;
        set
        {
            if (value == settings.TallyOn)
            {
                return;
            }

            settings.TallyOn = value;
            switchTally?.Invoke(value);
            OnPropertyChanged();
            Refresh();
        }
    }

    public string RulePanelTitle => strings.Get(IsAddingRule ? "Tally.AddRule" : "Tally.EditRule");

    public string CategoryPanelTitle => strings.Get(IsAddingCategory ? "Tally.AddCategory" : "Tally.EditCategory");

    /// <summary>An example of what the chosen kind of rule matches.</summary>
    public string PatternHint => strings.Get(DraftMatch.Id switch
    {
        TallyRules.Title => "Tally.PatternTitleHint",
        TallyRules.Folder => "Tally.PatternFolderHint",
        _ => "Tally.PatternAppHint",
    });

    public void Refresh()
    {
        var today = Today();
        var weekStart = GoalRules.PeriodStart(GoalHorizon.Week, today);
        var own = tally.Categories();
        labels = new TallyLabels(defaults, own, strings, areaBrush);
        var firstWeek = weekStart.AddDays(-7 * (RecentWeeks - 1));
        var recent = tally.Days(firstWeek, weekStart.AddDays(6));
        var week = recent.Where(day => day.Day >= weekStart).ToList();
        HasTime = week.Any(day => day.Minutes > 0);
        ShowChips(week);

        var kept = week.Where(day => (kind is null || day.DeviceKind == kind) && (category is null || day.Category == category)).ToList();
        var shown = pickedDay is { } picked && picked >= weekStart && picked <= today ? picked : today;
        IsToday = shown == today;
        DayTitle = IsToday ? strings.Get("Tally.Today") : shown.ToString("dddd d MMMM", CultureInfo.CurrentCulture).ToUpper(CultureInfo.CurrentCulture);
        DayEmpty = strings.Get(IsToday ? "Tally.NothingToday" : "Tally.NothingThatDay");
        DayChoice = IsToday ? strings.Get("Tally.TodayChoice") : shown.ToString("dddd", CultureInfo.CurrentCulture);
        var days = TallyRules.ByCategory(kept.Where(day => day.Day == shown));
        var dayMinutes = days.Sum(group => group.Minutes);
        DayTotal = labels.Duration(dayMinutes);
        HasDay = dayMinutes > 0;
        DayParts = Parts(days);
        DayLegend.Clear();
        foreach (var group in days)
        {
            DayLegend.Add(new TallySegmentViewModel(labels.Name(group.Key!), labels.Emoji(group.Key!), labels.Duration(group.Minutes), labels.Brush(group.Key!)));
        }

        ShowLocalStretches(weekStart, today, shown);

        var weekDays = Enumerable.Range(0, 7).Select(offset => weekStart.AddDays(offset)).ToList();
        var byDay = weekDays.Select(day => TallyRules.ByCategory(kept.Where(row => row.Day == day))).ToList();
        var most = Math.Max(1, byDay.Max(groups => groups.Sum(group => group.Minutes)));
        WeekDays.Clear();
        for (var index = 0; index < weekDays.Count; index++)
        {
            var minutes = byDay[index].Sum(group => group.Minutes);
            var day = weekDays[index];
            WeekDays.Add(new TallyBarViewModel(
                day.ToString("ddd", CultureInfo.CurrentCulture),
                minutes > 0 ? labels.Duration(minutes) : string.Empty,
                (double)minutes / most,
                Parts(byDay[index]),
                strings.Get("Tally.BarTip", day.ToString("dddd d MMM", CultureInfo.CurrentCulture), labels.Duration(minutes)),
                day == today,
                day == shown,
                new RelayCommand(() => ShowDay(day))));
        }

        WeekTotal = strings.Get("Tally.WeekTotal", labels.Duration(kept.Sum(day => day.Minutes)));
        ShowDevices(week);
        ShowRecentWeeks(recent, today);

        var names = projects.All().ToDictionary(project => project.Id, project => project.Name, StringComparer.Ordinal);
        var byProject = TallyRules.ByProject(kept).Where(group => group.Key is not null).ToList();
        var mostProject = Math.Max(1, byProject.Select(group => group.Minutes).DefaultIfEmpty(0).Max());
        Projects.Clear();
        foreach (var group in byProject)
        {
            Projects.Add(new TallyProjectViewModel(
                names.TryGetValue(group.Key!, out var name) ? name : strings.Get("Tally.GoneProject"),
                labels.Duration(group.Minutes),
                (double)group.Minutes / mostProject));
        }

        HasProjects = Projects.Count > 0;
        ShowRulesAndCategories(own);
    }

    /// <summary>Narrows the page to one device kind or one category, or lets go of the one already chosen.</summary>
    public void Choose(string chip)
    {
        if (chip is TallyRules.Phone or TallyRules.Pc)
        {
            kind = kind == chip ? null : chip;
        }
        else
        {
            category = category == chip ? null : chip;
        }

        Refresh();
    }

    /// <summary>Shows one day of the week closer up, or today again when that day is picked a second time.</summary>
    public void ShowDay(DateOnly day)
    {
        pickedDay = pickedDay == day ? null : day;
        Refresh();
    }

    /// <summary>Starts a new rule that puts one of this PC's apps into a category, for the owner to change the category.</summary>
    public void MakeAppRule(string forCategory, string app) => StartNewRule(TallyRules.App, app, forCategory);

    /// <summary>Starts a new rule from a site (a title rule) or an editor's folder (a folder rule) under an app.</summary>
    public void MakeWindowRule(string forCategory, string app, string label) => StartNewRule(TallyBreakdown.WindowMatch(app), label, forCategory);

    /// <summary>
    /// Sorts one of this PC's apps, sites or folders into <paramref name="into"/> in one step: a rule for
    /// the PC, or the owner's rule for exactly it changed. Today is counted again at once.
    /// </summary>
    public void SortInto(string match, string pattern, string into)
    {
        if (tally.SortInto(match, pattern, TallyRules.Windows, into))
        {
            recount?.Invoke();
        }
    }

    /// <summary>The categories to move something into from <paramref name="from"/>: every other one, the owner's own first.</summary>
    public IReadOnlyList<FilterChoiceViewModel> MoveChoices(string from) => [.. sortChoices.Where(choice => choice.Id != from)];

    public void StartEditRule(TallyRuleRowViewModel row)
    {
        editingRuleId = row.Rule.Id;
        IsAddingRule = false;
        DraftMatch = MatchChoices.FirstOrDefault(choice => choice.Id == row.Rule.Match) ?? MatchChoices[0];
        DraftPattern = row.Rule.Pattern;
        DraftPlatform = PlatformChoices.FirstOrDefault(choice => choice.Id == row.Rule.Platform) ?? PlatformChoices[0];
        DraftCategory = CategoryChoices.FirstOrDefault(choice => choice.Id == row.Rule.Category) ?? CategoryChoices.FirstOrDefault();
        DraftProject = ProjectChoices.FirstOrDefault(choice => choice.Id == row.Rule.Project) ?? ProjectChoices[0];
        RuleRefused = false;
        OnPropertyChanged(nameof(RulePanelTitle));
        IsEditingRule = true;
    }

    public void DeleteRule(TallyRuleRowViewModel row)
    {
        if (row.Rule.Id is { } id && tally.DeleteRule(id))
        {
            recount?.Invoke();
        }
    }

    public void StartEditCategory(TallyCategoryRowViewModel row)
    {
        editingCategoryId = row.Category.Id;
        IsAddingCategory = false;
        DraftName = row.Category.Name;
        DraftColor = Colors.FirstOrDefault(color => color.Id == row.Category.Color) ?? Colors.FirstOrDefault();
        DraftEmoji = row.Category.Emoji ?? string.Empty;
        CanMerge = true;
        MergeChoices = MoveChoices(row.Category.Id);
        MergeTarget = null;
        IsAskingMerge = false;
        OnPropertyChanged(nameof(CategoryPanelTitle));
        IsEditingCategory = true;
    }

    public void DeleteCategory(TallyCategoryRowViewModel row) => tally.DeleteCategory(row.Category.Id);

    [RelayCommand]
    private void ToggleChip(string chip) => Choose(chip);

    [RelayCommand]
    private void ShowToday()
    {
        pickedDay = null;
        Refresh();
    }

    /// <summary>Shows this PC's apps for the day shown ("day") or the whole week ("week").</summary>
    [RelayCommand]
    private void ShowApps(string scope)
    {
        AppsForWeek = scope == "week";
        Refresh();
    }

    [RelayCommand]
    private void AddRule()
    {
        editingRuleId = null;
        IsAddingRule = true;
        DraftMatch = MatchChoices[0];
        DraftPattern = string.Empty;
        DraftPlatform = PlatformChoices[0];
        DraftCategory = CategoryChoices.FirstOrDefault();
        DraftProject = ProjectChoices[0];
        RuleRefused = false;
        OnPropertyChanged(nameof(RulePanelTitle));
        IsEditingRule = true;
    }

    private bool CanSaveRule() => DraftPattern.Trim().Length > 0 && DraftCategory?.Id is not null;

    [RelayCommand(CanExecute = nameof(CanSaveRule))]
    private void SaveRule()
    {
        // The phone never links time to a project, so a rule for the phone alone keeps none.
        var project = DraftPlatform.Id == TallyRules.Android ? null : DraftProject?.Id;
        var rule = new TallyRule(DraftMatch.Id!, DraftPattern, DraftPlatform.Id!, DraftCategory!.Id!, project);
        var saved = editingRuleId is null ? tally.AddRule(rule) is not null : tally.UpdateRule(editingRuleId, rule);
        RuleRefused = !saved;
        if (saved)
        {
            IsEditingRule = false;
            // The day still being written is sorted again by the rule at once.
            recount?.Invoke();
        }
    }

    [RelayCommand]
    private void CancelRule() => IsEditingRule = false;

    [RelayCommand]
    private void AddCategory()
    {
        editingCategoryId = null;
        IsAddingCategory = true;
        DraftName = string.Empty;
        DraftColor = Colors.FirstOrDefault();
        DraftEmoji = string.Empty;
        CanMerge = false;
        MergeTarget = null;
        IsAskingMerge = false;
        OnPropertyChanged(nameof(CategoryPanelTitle));
        IsEditingCategory = true;
    }

    private bool CanSaveCategory() => DraftName.Trim().Length > 0;

    [RelayCommand(CanExecute = nameof(CanSaveCategory))]
    private void SaveCategory()
    {
        var color = DraftColor?.Id ?? Colors.FirstOrDefault()?.Id ?? string.Empty;
        var saved = editingCategoryId is null
            ? tally.AddCategory(DraftName, color, DraftEmoji) is not null
            : tally.UpdateCategory(editingCategoryId, DraftName, color, DraftEmoji);
        if (saved)
        {
            IsEditingCategory = false;
        }
    }

    [RelayCommand]
    private void CancelCategory()
    {
        IsAskingMerge = false;
        IsEditingCategory = false;
    }

    private bool CanAskMerge() => MergeTarget?.Id is not null;

    /// <summary>Asks on the page before the category being edited merges into the one picked.</summary>
    [RelayCommand(CanExecute = nameof(CanAskMerge))]
    private void AskMerge()
    {
        if (editingCategoryId is not { } from || MergeTarget?.Id is null)
        {
            return;
        }

        MergeQuestion = strings.Get("Tally.MergeQuestion", labels.Name(from), MergeTarget.Label);
        IsAskingMerge = true;
    }

    [RelayCommand]
    private void ConfirmMerge()
    {
        IsAskingMerge = false;
        if (editingCategoryId is { } from && MergeTarget?.Id is { } into && tally.MergeCategory(from, into))
        {
            IsEditingCategory = false;
            // Its rules now sort into the other category, so today is counted again.
            recount?.Invoke();
        }
    }

    [RelayCommand]
    private void CancelMerge() => IsAskingMerge = false;

    private void StartNewRule(string match, string pattern, string forCategory)
    {
        editingRuleId = null;
        IsAddingRule = true;
        DraftMatch = MatchChoices.FirstOrDefault(choice => choice.Id == match) ?? MatchChoices[0];
        DraftPattern = pattern;
        DraftPlatform = PlatformChoices.First(choice => choice.Id == TallyRules.Windows);
        DraftCategory = CategoryChoices.FirstOrDefault(choice => choice.Id == forCategory) ?? CategoryChoices.FirstOrDefault();
        DraftProject = ProjectChoices[0];
        RuleRefused = false;
        OnPropertyChanged(nameof(RulePanelTitle));
        IsEditingRule = true;
    }

    // This PC's own hours and apps from its log, while Tally is on here and the PC isn't filtered out.
    private void ShowLocalStretches(DateOnly weekStart, DateOnly today, DateOnly shown)
    {
        var counting = TallyOn && stretches is not null;
        ShowLocal = counting && kind != TallyRules.Phone;
        ShowLocalElsewhere = counting && kind == TallyRules.Phone;
        var local = ShowLocal ? stretches!(weekStart, today).Where(stretch => category is null || stretch.Category == category).ToList() : [];
        var startHour = settings.DayStartHour;

        Hours.Clear();
        var spoken = new List<string>();
        var hours = TallyBreakdown.Hours(local, shown, startHour);
        for (var index = 0; index < hours.Count; index++)
        {
            var hour = hours[index];
            var clock = Clock(hour.Hour);
            var tip = hour.Seconds > 0 ? strings.Get("Tally.HourTip", clock, labels.Duration(Math.Max(1, (hour.Seconds + 30) / 60))) : clock;
            if (hour.Seconds > 0)
            {
                spoken.Add(tip);
            }

            Hours.Add(new TallyHourViewModel(
                Math.Min(1, hour.Seconds / 3600d),
                [.. hour.Categories.Select(part => ((double)part.Seconds, labels.Brush(part.Category)))],
                tip));
        }

        HourMarks = [.. hours.Where((_, index) => index % 6 == 0).Select(hour => Clock(hour.Hour))];
        HoursDescription = spoken.Count == 0 ? strings.Get("Tally.HoursNone") : strings.Get("Tally.HoursSpoken", string.Join(", ", spoken));

        Apps.Clear();
        var groups = AppsForWeek ? TallyBreakdown.Apps(local, weekStart, today, startHour) : TallyBreakdown.Apps(local, shown, shown, startHour);
        foreach (var group in groups)
        {
            List<TallyAppRowViewModel> apps =
            [
                .. group.Apps.Select(app => new TallyAppRowViewModel(
                    this,
                    group.Category,
                    app.App,
                    labels.Duration(app.Minutes),
                    strings.Get("Tally.MakeRuleFor", app.App),
                    [
                        .. app.Windows.Select(window => new TallyWindowRowViewModel(
                            this,
                            group.Category,
                            app.App,
                            window.Label,
                            labels.Duration(window.Minutes),
                            strings.Get("Tally.MakeRuleFor", window.Label),
                            strings.Get("Tally.MoveToFor", window.Label))),
                    ],
                    strings.Get("Tally.MoveToFor", app.App))),
            ];
            Apps.Add(new TallyAppGroupViewModel(
                group.Category,
                labels.Name(group.Category),
                labels.Emoji(group.Category),
                labels.Brush(group.Category),
                labels.Duration(group.Minutes),
                apps,
                open.Contains(group.Category),
                Remember));
        }

        HasApps = Apps.Count > 0;
        ShowToSortList(groups);
    }

    // What landed in Other, while the page isn't narrowed to another category: each app, but for a
    // browser or an editor its sites or folders instead, since a rule for the whole browser would
    // come before the shipped site rules. The same site in two browsers is one line.
    private void ShowToSortList(IReadOnlyList<TallyCategoryApps> groups)
    {
        ShowToSort = category is null or TallyRules.Other;
        ToSort.Clear();
        var other = groups.FirstOrDefault(group => group.Category == TallyRules.Other);
        var items = new List<(string Match, string Pattern, string Detail, int Minutes)>();
        foreach (var app in other?.Apps ?? [])
        {
            if (app.Windows.Count == 0)
            {
                items.Add((TallyRules.App, app.App, strings.Get("Tally.SortApp"), app.Minutes));
                continue;
            }

            var match = TallyBreakdown.WindowMatch(app.App);
            var detail = strings.Get(match == TallyRules.Folder ? "Tally.SortFolder" : "Tally.SortSite", app.App);
            items.AddRange(app.Windows.Select(window => (match, window.Label, detail, window.Minutes)));
        }

        var lines = items
            .GroupBy(item => (item.Match, Pattern: item.Pattern.ToLowerInvariant()))
            .Select(same => (same.First().Match, same.First().Pattern, same.First().Detail, Minutes: same.Sum(item => item.Minutes)))
            .OrderByDescending(item => item.Minutes)
            .ThenBy(item => item.Pattern, StringComparer.OrdinalIgnoreCase);
        foreach (var line in lines)
        {
            ToSort.Add(new TallySortRowViewModel(
                this, line.Match, line.Pattern, line.Detail, labels.Duration(line.Minutes), strings.Get("Tally.MoveToFor", line.Pattern)));
        }

        HasToSort = ToSort.Count > 0;
    }

    // This week's minutes on the phone and on the PC, each a bar by category against the bigger one.
    // The category chip narrows them; the device chips don't, since the card compares the two.
    private void ShowDevices(IReadOnlyList<TallyDay> week)
    {
        var kept = week.Where(day => category is null || day.Category == category).ToList();
        (string Kind, string Name)[] kinds = [(TallyRules.Phone, strings.Get("Tally.Phone")), (TallyRules.Pc, strings.Get("Tally.Pc"))];
        var byKind = kinds.Select(one => TallyRules.ByCategory(kept.Where(day => day.DeviceKind == one.Kind))).ToList();
        var most = Math.Max(1, byKind.Max(groups => groups.Sum(group => group.Minutes)));
        Devices.Clear();
        for (var index = 0; index < kinds.Length; index++)
        {
            var minutes = byKind[index].Sum(group => group.Minutes);
            var name = kinds[index].Name;
            Devices.Add(new TallyBarViewModel(
                name, labels.Duration(minutes), (double)minutes / most, Parts(byKind[index]), strings.Get("Tally.BarTip", name, labels.Duration(minutes))));
        }
    }

    // The last eight weeks under the chips, as Stats shows twelve.
    private void ShowRecentWeeks(IReadOnlyList<TallyDay> recent, DateOnly today)
    {
        var weeks = TallyRules.Weeks(recent, today, RecentWeeks, kind, category);
        var most = Math.Max(1, weeks.Max(week => week.Minutes));
        RecentWeekBars.Clear();
        foreach (var week in weeks)
        {
            var label = week.Start.ToString("d MMM", CultureInfo.CurrentCulture);
            RecentWeekBars.Add(new TallyBarViewModel(
                label,
                week.Minutes > 0 ? labels.Duration(week.Minutes) : string.Empty,
                (double)week.Minutes / most,
                Parts(week.Categories),
                strings.Get("Tally.BarTip", label, labels.Duration(week.Minutes)),
                week == weeks[^1]));
        }

        RecentLegend.Clear();
        var kept = recent.Where(day => (kind is null || day.DeviceKind == kind) && (category is null || day.Category == category));
        foreach (var group in TallyRules.ByCategory(kept))
        {
            RecentLegend.Add(new TallySegmentViewModel(labels.Name(group.Key!), labels.Emoji(group.Key!), labels.Duration(group.Minutes), labels.Brush(group.Key!)));
        }

        var minutes = weeks.Sum(week => week.Minutes);
        RecentTotal = strings.Get("Tally.RecentTotal", labels.Duration(minutes));
        RecentFirstWeek = weeks[0].Start.ToString("d MMM", CultureInfo.CurrentCulture);
        HasRecentWeeks = minutes > 0;
    }

    // Keeps which categories are open across a refresh.
    private void Remember(string opened, bool isOpen)
    {
        if (isOpen)
        {
            open.Add(opened);
        }
        else
        {
            open.Remove(opened);
        }
    }

    private static string Clock(int hour) => hour.ToString("00", CultureInfo.InvariantCulture) + ":00";

    // Phone and PC always; a category once it has time this week, or while it is the chosen one.
    private void ShowChips(IReadOnlyList<TallyDay> week)
    {
        Chips.Clear();
        Chips.Add(new FilterOptionViewModel(TallyRules.Phone, strings.Get("Tally.Phone"), null, kind == TallyRules.Phone, ToggleChipCommand));
        Chips.Add(new FilterOptionViewModel(TallyRules.Pc, strings.Get("Tally.Pc"), null, kind == TallyRules.Pc, ToggleChipCommand));
        var shown = TallyRules.ByCategory(week).Select(group => group.Key!).ToList();
        if (category is not null && !shown.Contains(category))
        {
            shown.Add(category);
        }

        foreach (var id in shown)
        {
            Chips.Add(new FilterOptionViewModel(id, labels.Name(id), labels.Brush(id), category == id, ToggleChipCommand));
        }
    }

    private void ShowRulesAndCategories(IReadOnlyList<TallyCategory> own)
    {
        // The pickers are rebuilt only when their lines change, so an open panel keeps its choice.
        var choices = defaults.Categories.Concat(own).Select(one => new FilterChoiceViewModel(one.Id, labels.Name(one.Id), labels.Brush(one.Id))).ToList();
        if (!choices.Select(choice => (choice.Id, choice.Label)).SequenceEqual(CategoryChoices.Select(choice => (choice.Id, choice.Label))))
        {
            CategoryChoices = choices;
        }

        sortChoices = [.. own.Concat(defaults.Categories).Select(one => new FilterChoiceViewModel(one.Id, labels.Name(one.Id), labels.Brush(one.Id)))];

        List<ChoiceViewModel> projectChoices = [new(null, strings.Get("Tally.NoProject")), .. projects.All().Select(project => new ChoiceViewModel(project.Id, project.Name))];
        if (!projectChoices.SequenceEqual(ProjectChoices))
        {
            ProjectChoices = projectChoices;
        }

        Rules.Clear();
        var names = projects.All().ToDictionary(project => project.Id, project => project.Name, StringComparer.Ordinal);
        foreach (var rule in tally.Rules())
        {
            var match = MatchChoices.FirstOrDefault(choice => choice.Id == rule.Match)?.Label ?? rule.Match;
            var platform = PlatformChoices.FirstOrDefault(choice => choice.Id == rule.Platform)?.Label ?? rule.Platform;
            var detail = strings.Get("Tally.RuleDetail", match, platform, labels.Name(rule.Category));
            if (rule.Project is { } project)
            {
                detail = strings.Get("Tally.RuleWithProject", detail, names.TryGetValue(project, out var name) ? name : strings.Get("Tally.GoneProject"));
            }

            Rules.Add(new TallyRuleRowViewModel(this, rule, detail, labels.Brush(rule.Category)));
        }

        Categories.Clear();
        foreach (var one in own)
        {
            Categories.Add(new TallyCategoryRowViewModel(this, one, labels.Brush(one.Id)));
        }

        HasRules = Rules.Count > 0;
        HasCategories = Categories.Count > 0;
    }

    private IReadOnlyList<(double Amount, Brush? Brush)> Parts(IEnumerable<TallyMinutes> groups) =>
        [.. groups.Select(group => ((double)group.Minutes, labels.Brush(group.Key!)))];

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}

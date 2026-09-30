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
/// and what syncs, filter chips (Phone, PC, a category), today as one stacked bar by category, the
/// week as stacked bars per day, time per project, and the owner's rules and categories with a panel
/// each to add and edit them. Every device's time counts; the chips narrow it.
/// </summary>
public sealed partial class TallyViewModel : ObservableObject
{
    private readonly TallyList tally;
    private readonly TallyDefaults defaults;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Action<bool>? switchTally;
    private TallyLabels labels;
    private string? kind;
    private string? category;
    private string? editingRuleId;
    private string? editingCategoryId;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoTime))]
    private bool hasTime;

    [ObservableProperty]
    private string todayTotal = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoToday))]
    private bool hasToday;

    [ObservableProperty]
    private IReadOnlyList<(double Amount, Brush? Brush)> todayParts = [];

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
        Action<bool>? switchTally = null)
    {
        this.tally = tally;
        this.defaults = defaults;
        this.projects = projects;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.switchTally = switchTally;
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

    public ObservableCollection<TallySegmentViewModel> TodayLegend { get; } = [];

    /// <summary>Monday to Sunday of this planning week.</summary>
    public ObservableCollection<TallyBarViewModel> WeekDays { get; } = [];

    public ObservableCollection<TallyProjectViewModel> Projects { get; } = [];

    public ObservableCollection<TallyRuleRowViewModel> Rules { get; } = [];

    public ObservableCollection<TallyCategoryRowViewModel> Categories { get; } = [];

    public IReadOnlyList<ChoiceViewModel> MatchChoices { get; }

    public IReadOnlyList<ChoiceViewModel> PlatformChoices { get; }

    public IReadOnlyList<ColorOptionViewModel> Colors { get; }

    public bool HasNoTime => !HasTime;

    public bool HasNoToday => !HasToday;

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
        var week = tally.Days(weekStart, weekStart.AddDays(6));
        HasTime = week.Any(day => day.Minutes > 0);
        ShowChips(week);

        var kept = week.Where(day => (kind is null || day.DeviceKind == kind) && (category is null || day.Category == category)).ToList();
        var todays = TallyRules.ByCategory(kept.Where(day => day.Day == today));
        var todayMinutes = todays.Sum(group => group.Minutes);
        TodayTotal = labels.Duration(todayMinutes);
        HasToday = todayMinutes > 0;
        TodayParts = Parts(todays);
        TodayLegend.Clear();
        foreach (var group in todays)
        {
            TodayLegend.Add(new TallySegmentViewModel(labels.Name(group.Key!), labels.Emoji(group.Key!), labels.Duration(group.Minutes), labels.Brush(group.Key!)));
        }

        var days = Enumerable.Range(0, 7).Select(offset => weekStart.AddDays(offset)).ToList();
        var byDay = days.Select(day => TallyRules.ByCategory(kept.Where(row => row.Day == day))).ToList();
        var most = Math.Max(1, byDay.Max(groups => groups.Sum(group => group.Minutes)));
        WeekDays.Clear();
        for (var index = 0; index < days.Count; index++)
        {
            var minutes = byDay[index].Sum(group => group.Minutes);
            WeekDays.Add(new TallyBarViewModel(
                days[index].ToString("ddd", CultureInfo.CurrentCulture),
                minutes > 0 ? labels.Duration(minutes) : string.Empty,
                (double)minutes / most,
                Parts(byDay[index]),
                strings.Get("Tally.BarTip", days[index].ToString("dddd d MMM", CultureInfo.CurrentCulture), labels.Duration(minutes)),
                days[index] == today));
        }

        WeekTotal = strings.Get("Tally.WeekTotal", labels.Duration(kept.Sum(day => day.Minutes)));

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
        if (row.Rule.Id is { } id)
        {
            tally.DeleteRule(id);
        }
    }

    public void StartEditCategory(TallyCategoryRowViewModel row)
    {
        editingCategoryId = row.Category.Id;
        IsAddingCategory = false;
        DraftName = row.Category.Name;
        DraftColor = Colors.FirstOrDefault(color => color.Id == row.Category.Color) ?? Colors.FirstOrDefault();
        DraftEmoji = row.Category.Emoji ?? string.Empty;
        OnPropertyChanged(nameof(CategoryPanelTitle));
        IsEditingCategory = true;
    }

    public void DeleteCategory(TallyCategoryRowViewModel row) => tally.DeleteCategory(row.Category.Id);

    [RelayCommand]
    private void ToggleChip(string chip) => Choose(chip);

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
    private void CancelCategory() => IsEditingCategory = false;

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

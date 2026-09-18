using System.Collections.ObjectModel;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The composer on a list: the line, its live preview (docs/composer.md) and saving it. The list
/// supplies the day when the line names none.
/// </summary>
public sealed partial class ComposerViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Func<DateOnly, DateOnly?> defaultDay;
    private readonly Action? openPlan;
    private ComposerDraft draft;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTaskCommand))]
    private string newTaskTitle = string.Empty;

    [ObservableProperty]
    private bool hasChips;

    /// <param name="defaultDay">The day a line without one lands on, from the planning day (null: none).</param>
    /// <param name="openPlan">What `/plan` does (docs/plan-tomorrow.md); null where it can't run.</param>
    public ComposerViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Func<DateOnly, DateOnly?> defaultDay,
        Action<Action> runOnUi,
        Action? openPlan = null)
    {
        this.openPlan = openPlan;
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.defaultDay = defaultDay;
        draft = Parse(string.Empty);
        areas.Changed += (_, _) => runOnUi(UpdatePreview);
        tags.Changed += (_, _) => runOnUi(UpdatePreview);
    }

    /// <summary>What the line will save, as it's typed.</summary>
    public ObservableCollection<ComposerChipViewModel> Chips { get; } = [];

    partial void OnNewTaskTitleChanged(string value) => UpdatePreview();

    private bool IsPlanCommand => draft.Command?.Name == PlanRules.Command && openPlan is not null;

    private bool CanAddTask() => (draft.Title.Trim().Length > 0 && draft.Command is null) || IsPlanCommand;

    [RelayCommand(CanExecute = nameof(CanAddTask))]
    private void AddTask()
    {
        if (IsPlanCommand)
        {
            NewTaskTitle = string.Empty;
            openPlan?.Invoke();
            return;
        }

        var placed = draft.PlannedDate is null && defaultDay(Today()) is { } day ? draft with { PlannedDate = day } : draft;
        if (tasks.Add(placed) is not null)
        {
            NewTaskTitle = string.Empty;
        }
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    private ComposerDraft Parse(string line) => ComposerParser.Parse(line, time.GetLocalNow().DateTime, settings.DayStartHour);

    private void UpdatePreview()
    {
        draft = Parse(NewTaskTitle);
        Chips.Clear();
        foreach (var chip in ComposerChips.Build(NewTaskTitle, draft, Today(), areas.All(), tags.Names(), strings, areaBrush, Remove))
        {
            Chips.Add(chip);
        }

        HasChips = Chips.Count > 0;
        AddTaskCommand.NotifyCanExecuteChanged();
    }

    private void Remove(ComposerChipViewModel chip) => NewTaskTitle = ComposerChips.RemoveParts(NewTaskTitle, chip.Spans);
}

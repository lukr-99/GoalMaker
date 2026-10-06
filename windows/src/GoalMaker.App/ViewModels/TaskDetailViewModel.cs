using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One task's detail page (spec stories 12 to 19, docs/archive.md): title and done box, notes in
/// light Markdown, day, time and deadline, area, the goal it serves, repeat, tags and the checklist. A
/// project item shows its id (GM-12) once the server has numbered it, with a way to copy it. Each field saves when
/// it changes; a refused value goes back to what was saved. Rebuilt in place when the replica
/// changes, so editing one field doesn't take the keyboard from another. The notes save on their
/// own: after a short pause in typing, when the box loses focus, and when the page closes or another
/// task opens, so no edit is lost and there is no button to remember.
/// </summary>
public sealed partial class TaskDetailViewModel : ObservableObject
{
    // "%H", not "H": a lone letter is a standard format, and there is no standard "H".
    private static readonly string[] TimeFormats = ["H:mm", "HH:mm", "H.mm", "%H"];

    // How long typing in the notes rests before the notes save.
    private static readonly TimeSpan NotesPause = TimeSpan.FromMilliseconds(800);
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly StepList steps;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<AppPage> openPage;
    private readonly Action<Action> runOnUi;
    private readonly GoalList? goals;
    private readonly ISettingsStore? settings;
    private readonly ProjectList? projects;
    private readonly Action<string>? copyText;
    private string? taskId;
    private AppPage back = AppPage.Today;
    private bool loading;
    private string title = string.Empty;
    private bool isDone;
    private DateTime? plannedDate;
    private string plannedTimeText = string.Empty;
    private DateTime? deadline;
    private ChoiceViewModel? selectedArea;
    private ChoiceViewModel? selectedRepeat;
    private ChoiceViewModel? selectedGoal;
    private ITimer? notesTimer;

    [ObservableProperty]
    private bool hasTask;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasItemId), nameof(TitleName))]
    [NotifyCanExecuteChangedFor(nameof(CopyIdCommand))]
    private string itemId = string.Empty;

    [ObservableProperty]
    private string notes = string.Empty;

    [ObservableProperty]
    private string notesDraft = string.Empty;

    [ObservableProperty]
    private bool isEditingNotes;

    /// <summary>Whether the notes were just saved on their own, for the quiet "Saved" next to them.</summary>
    [ObservableProperty]
    private bool notesSaved;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> areaChoices = [];

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> repeatChoices = [];

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> goalChoices = [];

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTagCommand))]
    private string newTagName = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddStepCommand))]
    private string newStepTitle = string.Empty;

    public TaskDetailViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        StepList steps,
        IStrings strings,
        TimeProvider time,
        Action<Action> runOnUi,
        Action<AppPage> openPage,
        GoalList? goals = null,
        ISettingsStore? settings = null,
        ProjectList? projects = null,
        Action<string>? copyText = null)
    {
        this.projects = projects;
        this.copyText = copyText;
        this.goals = goals;
        this.settings = settings;
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.steps = steps;
        this.strings = strings;
        this.time = time;
        this.openPage = openPage;
        this.runOnUi = runOnUi;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        steps.Changed += (_, _) => runOnUi(Refresh);
        if (goals is not null)
        {
            goals.Changed += (_, _) => runOnUi(Refresh);
        }

        if (projects is not null)
        {
            projects.Changed += (_, _) => runOnUi(Refresh);
        }
    }

    /// <summary>Whether the page offers the goal picker (there is a goal list to pick from).</summary>
    public bool HasGoals => goals is not null;

    public ObservableCollection<FilterOptionViewModel> Tags { get; } = [];

    public ObservableCollection<StepRowViewModel> Steps { get; } = [];

    public bool HasNotes => Notes.Length > 0;

    /// <summary>A project item's id, GM-12, is on show; a new item has none until the server numbers it.</summary>
    public bool HasItemId => ItemId.Length > 0;

    /// <summary>What a screen reader calls the title box: "Title", or "Title of GM-12" for a numbered item.</summary>
    public string TitleName => HasItemId ? strings.Get("Task.TitleWithId", ItemId) : strings.Get("Task.TitleLabel");

    public bool HasPlannedDate => PlannedDate is not null;

    public string Title
    {
        get => title;
        set => Commit(ref title, value.Trim(), id => tasks.Rename(id, value));
    }

    public bool IsDone
    {
        get => isDone;
        set => Commit(ref isDone, value, id =>
        {
            tasks.SetDone(id, value);
            return true;
        });
    }

    public DateTime? PlannedDate
    {
        get => plannedDate;
        set
        {
            Commit(ref plannedDate, value?.Date, id =>
            {
                tasks.Schedule(id, value is { } day ? DateOnly.FromDateTime(day) : null, ParseTime(plannedTimeText));
                return true;
            });
            OnPropertyChanged(nameof(HasPlannedDate));
        }
    }

    /// <summary>The planned time as typed ("9:30"); empty for none. Something that isn't a time goes back to what was saved.</summary>
    public string PlannedTimeText
    {
        get => plannedTimeText;
        set
        {
            var parsed = ParseTime(value);
            var ok = value.Trim().Length == 0 || parsed is not null;
            Commit(ref plannedTimeText, parsed?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty, id =>
            {
                if (ok && plannedDate is { } day)
                {
                    tasks.Schedule(id, DateOnly.FromDateTime(day), parsed);
                }

                return ok;
            });
        }
    }

    public DateTime? Deadline
    {
        get => deadline;
        set => Commit(ref deadline, value?.Date, id =>
        {
            tasks.SetDeadline(id, value is { } day ? DateOnly.FromDateTime(day) : null);
            return true;
        });
    }

    public ChoiceViewModel? SelectedArea
    {
        get => selectedArea;
        set => Commit(ref selectedArea, value, id =>
        {
            tasks.SetArea(id, value?.Id);
            return true;
        });
    }

    /// <summary>The goal the task serves (docs/goals.md), or none.</summary>
    public ChoiceViewModel? SelectedGoal
    {
        get => selectedGoal;
        set => Commit(ref selectedGoal, value, id =>
        {
            tasks.SetGoal(id, value?.Id);
            return true;
        });
    }

    public ChoiceViewModel? SelectedRepeat
    {
        get => selectedRepeat;
        set => Commit(ref selectedRepeat, value, id => tasks.SetRecurrence(id, value?.Id));
    }

    /// <summary>Shows <paramref name="id"/>; <paramref name="from"/> is the page Back returns to.</summary>
    public void Load(string id, AppPage from)
    {
        SaveNotes();
        taskId = id;
        back = from;
        IsEditingNotes = false;
        NotesSaved = false;
        Steps.Clear();
        Refresh();
    }

    public void Refresh()
    {
        if (taskId is null || tasks.Find(taskId) is not { } task)
        {
            HasTask = false;
            ItemId = string.Empty;
            return;
        }

        loading = true;
        try
        {
            HasTask = true;
            ItemId = ProjectRules.ItemIdOf(task, task.ProjectId is { } projectId ? projects?.Get(projectId) : null) ?? string.Empty;
            Set(ref title, task.Title, nameof(Title));
            Set(ref isDone, task.State == TaskState.Done, nameof(IsDone));
            Notes = task.Notes;
            if (!IsEditingNotes)
            {
                NotesDraft = task.Notes;
            }

            Set(ref plannedDate, task.PlannedDate?.ToDateTime(TimeOnly.MinValue), nameof(PlannedDate));
            OnPropertyChanged(nameof(HasPlannedDate));
            Set(ref plannedTimeText, task.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty, nameof(PlannedTimeText));
            Set(ref deadline, task.Deadline?.ToDateTime(TimeOnly.MinValue), nameof(Deadline));

            // Archived areas leave the picker, but the task's own area stays listed.
            AreaChoices = [new ChoiceViewModel(null, strings.Get("Task.NoArea")), .. areas.All().Where(area => !area.Archived || area.Id == task.AreaId).Select(area => new ChoiceViewModel(area.Id, area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name))];
            Set(ref selectedArea, AreaChoices.FirstOrDefault(choice => choice.Id == task.AreaId) ?? AreaChoices[0], nameof(SelectedArea));

            // The goals a task can serve: the open ones whose period hasn't ended, and the one it serves now.
            if (goals is not null)
            {
                var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings?.DayStartHour ?? PlanningDay.DefaultStartHour);
                GoalChoices = [new ChoiceViewModel(null, strings.Get("Task.NoGoal")), .. goals.All()
                    .Where(goal => goal.Id == task.GoalId || (goal.Status == GoalRules.Open && GoalRules.PeriodEnd(goal.Horizon, goal.PeriodStart) >= today))
                    .Select(goal => new ChoiceViewModel(
                        goal.Id,
                        $"{(goal.Emoji is { } emoji ? emoji + " " : string.Empty)}{goal.Title} · {strings.Get("Goals.Horizon" + goal.Horizon)}"))];
                Set(ref selectedGoal, GoalChoices.FirstOrDefault(choice => choice.Id == task.GoalId) ?? GoalChoices[0], nameof(SelectedGoal));
            }

            var anchor = task.PlannedDate ?? DateOnly.FromDateTime(time.GetLocalNow().DateTime);
            var rules = new List<string>
            {
                "FREQ=DAILY",
                "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
                "FREQ=WEEKLY;BYDAY=" + anchor.DayOfWeek.ToString()[..2].ToUpperInvariant(),
                "FREQ=MONTHLY;BYMONTHDAY=" + anchor.Day.ToString(CultureInfo.InvariantCulture),
            };
            if (task.Recurrence is { } current && !rules.Contains(current))
            {
                rules.Add(current);
            }

            RepeatChoices = [new ChoiceViewModel(null, strings.Get("Task.NoRepeat")), .. rules.Select(rule => new ChoiceViewModel(rule, ComposerChips.DescribeRepeat(rule, strings)))];
            Set(ref selectedRepeat, RepeatChoices.FirstOrDefault(choice => choice.Id == task.Recurrence) ?? RepeatChoices[0], nameof(SelectedRepeat));

            var linked = tags.ForTask(task.Id).Select(tag => tag.Id).ToHashSet(StringComparer.Ordinal);
            Tags.Clear();
            foreach (var tag in tags.All())
            {
                Tags.Add(new FilterOptionViewModel(tag.Id, "#" + tag.Name, null, linked.Contains(tag.Id), new RelayCommand(() => ToggleTag(tag, linked.Contains(tag.Id)))));
            }

            var stepList = steps.ForTask(task.Id);
            if (!Steps.Select(row => row.Id).SequenceEqual(stepList.Select(step => step.Id)))
            {
                Steps.Clear();
                foreach (var step in stepList)
                {
                    Steps.Add(new StepRowViewModel(step, steps, MoveStep));
                }
            }

            for (var index = 0; index < stepList.Count; index++)
            {
                Steps[index].Update(stepList[index], index == 0, index == stepList.Count - 1);
            }
        }
        finally
        {
            loading = false;
        }
    }

    partial void OnNotesChanged(string value) => OnPropertyChanged(nameof(HasNotes));

    // Typing in the notes saves them once it rests for a moment.
    partial void OnNotesDraftChanged(string value)
    {
        if (!IsEditingNotes || loading)
        {
            return;
        }

        NotesSaved = false;
        notesTimer?.Dispose();
        notesTimer = time.CreateTimer(_ => runOnUi(SaveNotes), null, NotesPause, Timeout.InfiniteTimeSpan);
    }

    /// <summary>
    /// Writes the notes being edited when they differ from the saved ones. The page calls it when it
    /// closes; a pause in typing and opening another task call it too.
    /// </summary>
    public void SaveNotes()
    {
        notesTimer?.Dispose();
        notesTimer = null;
        if (!IsEditingNotes || taskId is not { } id || tasks.Find(id) is not { } task || NotesDraft == task.Notes)
        {
            return;
        }

        tasks.SetNotes(id, NotesDraft);
        NotesSaved = true;
    }

    /// <summary>The notes box lost focus: the notes save and show as text again.</summary>
    public void FinishNotes()
    {
        SaveNotes();
        IsEditingNotes = false;
    }

    [RelayCommand]
    private void Back()
    {
        FinishNotes();
        openPage(back);
    }

    /// <summary>Puts the item's id on the clipboard.</summary>
    [RelayCommand(CanExecute = nameof(CanCopyId))]
    private void CopyId() => copyText?.Invoke(ItemId);

    private bool CanCopyId() => HasItemId && copyText is not null;

    [RelayCommand]
    private void Delete()
    {
        notesTimer?.Dispose();
        notesTimer = null;
        IsEditingNotes = false;
        if (taskId is { } id)
        {
            tasks.Delete(id);
        }

        openPage(back);
    }

    [RelayCommand]
    private void EditNotes()
    {
        NotesDraft = Notes;
        NotesSaved = false;
        IsEditingNotes = true;
    }

    [RelayCommand]
    private void ClearDay() => PlannedDate = null;

    [RelayCommand]
    private void ClearDeadline() => Deadline = null;

    private bool CanAddTag() => NewTagName.Trim().TrimStart('#').Length > 0;

    [RelayCommand(CanExecute = nameof(CanAddTag))]
    private void AddTag()
    {
        if (taskId is { } id)
        {
            tasks.SetTags(id, [.. tags.ForTask(id).Select(tag => tag.Name), NewTagName.Trim().TrimStart('#')]);
            NewTagName = string.Empty;
        }
    }

    private bool CanAddStep() => NewStepTitle.Trim().Length > 0;

    [RelayCommand(CanExecute = nameof(CanAddStep))]
    private void AddStep()
    {
        if (taskId is { } id && steps.Add(id, NewStepTitle) is not null)
        {
            NewStepTitle = string.Empty;
        }
    }

    private static TimeOnly? ParseTime(string text) =>
        TimeOnly.TryParseExact(text.Trim(), TimeFormats, CultureInfo.InvariantCulture, DateTimeStyles.None, out var parsed)
            || TimeOnly.TryParse(text.Trim(), CultureInfo.CurrentCulture, out parsed)
            ? parsed
            : null;

    private void ToggleTag(TagItem tag, bool linked)
    {
        if (taskId is { } id)
        {
            var names = tags.ForTask(id).Select(item => item.Name).ToList();
            tasks.SetTags(id, linked ? names.Where(name => name != tag.Name) : [.. names, tag.Name]);
        }
    }

    private void MoveStep(string id, int by)
    {
        var index = Steps.ToList().FindIndex(row => row.Id == id);
        if (index >= 0)
        {
            steps.Move(id, index + by);
        }
    }

    // A change from the page: saved when the task takes it, otherwise the field shows what was saved.
    private void Commit<T>(ref T field, T value, Func<string, bool> save, [System.Runtime.CompilerServices.CallerMemberName] string? property = null)
    {
        if (loading || taskId is not { } id || EqualityComparer<T>.Default.Equals(field, value))
        {
            OnPropertyChanged(property);
            return;
        }

        var previous = field;
        field = value;
        if (!save(id))
        {
            field = previous;
        }

        OnPropertyChanged(property);
    }

    private void Set<T>(ref T field, T value, string property)
    {
        if (!EqualityComparer<T>.Default.Equals(field, value))
        {
            field = value;
            OnPropertyChanged(property);
        }
    }
}

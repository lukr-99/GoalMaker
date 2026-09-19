using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One task's detail page (spec stories 12 to 19, docs/archive.md): title and done box, notes in
/// light Markdown, day, time and deadline, area, repeat, tags and the checklist. Each field saves when
/// it changes; a refused value goes back to what was saved. Rebuilt in place when the replica
/// changes, so editing one field doesn't take the keyboard from another.
/// </summary>
public sealed partial class TaskDetailViewModel : ObservableObject
{
    // "%H", not "H": a lone letter is a standard format, and there is no standard "H".
    private static readonly string[] TimeFormats = ["H:mm", "HH:mm", "H.mm", "%H"];
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly StepList steps;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<AppPage> openPage;
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

    [ObservableProperty]
    private bool hasTask;

    [ObservableProperty]
    private string notes = string.Empty;

    [ObservableProperty]
    private string notesDraft = string.Empty;

    [ObservableProperty]
    private bool isEditingNotes;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> areaChoices = [];

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> repeatChoices = [];

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTagCommand))]
    private string newTagName = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddStepCommand))]
    private string newStepTitle = string.Empty;

    public TaskDetailViewModel(
        TaskList tasks, AreaList areas, TagList tags, StepList steps, IStrings strings, TimeProvider time, Action<Action> runOnUi, Action<AppPage> openPage)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.steps = steps;
        this.strings = strings;
        this.time = time;
        this.openPage = openPage;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        steps.Changed += (_, _) => runOnUi(Refresh);
    }

    public ObservableCollection<FilterOptionViewModel> Tags { get; } = [];

    public ObservableCollection<StepRowViewModel> Steps { get; } = [];

    public bool HasNotes => Notes.Length > 0;

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

    public ChoiceViewModel? SelectedRepeat
    {
        get => selectedRepeat;
        set => Commit(ref selectedRepeat, value, id => tasks.SetRecurrence(id, value?.Id));
    }

    /// <summary>Shows <paramref name="id"/>; <paramref name="from"/> is the page Back returns to.</summary>
    public void Load(string id, AppPage from)
    {
        taskId = id;
        back = from;
        IsEditingNotes = false;
        Steps.Clear();
        Refresh();
    }

    public void Refresh()
    {
        if (taskId is null || tasks.Find(taskId) is not { } task)
        {
            HasTask = false;
            return;
        }

        loading = true;
        try
        {
            HasTask = true;
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

            AreaChoices = [new ChoiceViewModel(null, strings.Get("Task.NoArea")), .. areas.All().Select(area => new ChoiceViewModel(area.Id, area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name))];
            Set(ref selectedArea, AreaChoices.FirstOrDefault(choice => choice.Id == task.AreaId) ?? AreaChoices[0], nameof(SelectedArea));

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

    [RelayCommand]
    private void Back() => openPage(back);

    [RelayCommand]
    private void Delete()
    {
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
        IsEditingNotes = true;
    }

    [RelayCommand]
    private void SaveNotes()
    {
        if (taskId is { } id)
        {
            tasks.SetNotes(id, NotesDraft);
        }

        IsEditingNotes = false;
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

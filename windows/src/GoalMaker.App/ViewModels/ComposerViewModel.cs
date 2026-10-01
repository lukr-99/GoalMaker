using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The composer on a list: the line, its live preview (docs/composer.md) and saving it. The list
/// supplies the day when the line names none. With the quick chat (M7) switched on, the line goes to
/// the chat instead, and quick-add is exactly as before whenever the switch is on quick-add. On
/// Today, Tomorrow and the Inbox the empty bar's plus opens the new task form (<see cref="AttachForm"/>).
/// </summary>
public sealed class ComposerViewModel : BarViewModel
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Func<DateOnly, DateOnly?> defaultDay;
    private readonly Action? openPlan;
    private readonly Action<string>? openWant;
    private Action<ComposerDraft?, Action>? openForm;
    private ComposerDraft draft;

    /// <param name="defaultDay">The day a line without one lands on, from the planning day (null: none).</param>
    /// <param name="openPlan">What `/plan` does (docs/plan-tomorrow.md); null where it can't run.</param>
    /// <param name="openWant">What `/want` does with its title (docs/wants.md); null where it can't run.</param>
    /// <param name="chat">The quick chat all composers share; null where the composer only adds tasks.</param>
    public ComposerViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Func<DateOnly, DateOnly?> defaultDay,
        Action<Action> runOnUi,
        Action? openPlan = null,
        Action<string>? openWant = null,
        ChatViewModel? chat = null)
        : base(strings, chat)
    {
        this.openPlan = openPlan;
        this.openWant = openWant;
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.projects = projects;
        this.settings = settings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.defaultDay = defaultDay;
        draft = Parse(string.Empty);
        areas.Changed += (_, _) => runOnUi(RefreshPreview);
        tags.Changed += (_, _) => runOnUi(RefreshPreview);
    }

    /// <summary>The line, under the name the lists have always used.</summary>
    public string NewTaskTitle
    {
        get => Line;
        set => Line = value;
    }

    /// <summary>Enter: saves the line as a task, runs its command, or sends it to the chat.</summary>
    public IAsyncRelayCommand AddTaskCommand => SendCommand;

    public override string FormName => Strings.Get("Composer.NewTask");

    /// <summary>Raised after a line was saved as a task, so a quick-add box can close.</summary>
    public event EventHandler? Added;

    protected override string ItemPlaceholder => Strings.Get("Composer.Placeholder");

    protected override string AddName => Strings.Get("Composer.Add");

    private bool IsPlanCommand => draft.Command?.Name == PlanRules.Command && openPlan is not null;

    private bool IsWantCommand => draft.Command?.Name == WantRules.Command && openWant is not null;

    /// <summary>
    /// Gives the empty bar's plus a form to open: <paramref name="open"/> gets what the line says (null
    /// for a blank form) and what to do once the form saved it.
    /// </summary>
    public void AttachForm(Action<ComposerDraft?, Action> open)
    {
        openForm = open;
        HasForm = true;
    }

    /// <summary>The draft a line gives on this list, with the list's own day when it names none.</summary>
    public ComposerDraft Placed(ComposerDraft parsed) =>
        parsed.PlannedDate is null && defaultDay(Today()) is { } day ? parsed with { PlannedDate = day } : parsed;

    /// <summary>The day a line without one lands on here; null for none (the Inbox).</summary>
    public DateOnly? DefaultDay() => defaultDay(Today());

    protected override void OnLineEdited()
    {
        draft = Parse(Line);
        OnPropertyChanged(nameof(NewTaskTitle));
    }

    protected override bool CanAdd() => (draft.Title.Trim().Length > 0 && draft.Command is null) || IsPlanCommand || IsWantCommand;

    protected override bool Add()
    {
        if (IsPlanCommand)
        {
            openPlan?.Invoke();
            return true;
        }

        if (IsWantCommand)
        {
            openWant?.Invoke(draft.Command!.Argument);
            return true;
        }

        if (tasks.Add(Placed(draft)) is null)
        {
            return false;
        }

        Added?.Invoke(this, EventArgs.Empty);
        return true;
    }

    protected override void OpenFormWith(string text)
    {
        if (openForm is null)
        {
            return;
        }

        var parsed = text.Length > 0 && Parse(text) is { Command: null } read ? Placed(read) : null;
        openForm(parsed, () => ClearIf(text));
    }

    protected override IEnumerable<ComposerChipViewModel> BuildChips() =>
        ComposerChips.Build(Line, draft, Today(), areas.All(), tags.Names(), projects.All(), Strings, areaBrush, Remove);

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    private ComposerDraft Parse(string text) => ComposerParser.Parse(text, time.GetLocalNow().DateTime, settings.DayStartHour);

    private void Remove(ComposerChipViewModel chip) => Line = ComposerChips.RemoveParts(Line, chip.Spans);
}

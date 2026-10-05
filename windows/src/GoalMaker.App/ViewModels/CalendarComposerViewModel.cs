using System.Globalization;
using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The calendar's bottom bar (docs/calendar.md, "Adding from the calendar"): it adds to the picked day
/// a task, read by the task rules and planned for that day, or an event, whose line is its title, by
/// its Task or Event switch. With several days picked it asks instead: one event from the first picked
/// day to the last, or a copy of the task on each day (<see cref="EventRules.PickedEvent"/>,
/// <see cref="EventRules.PickedTaskDays"/>), starting at the choice used last on this PC. Each add
/// hands the page an undo that takes back the whole batch. In event mode the plus opens the event
/// editor on the picked days.
/// </summary>
public sealed class CalendarComposerViewModel : BarViewModel
{
    private readonly TaskList tasks;
    private readonly EventList? events;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Func<IReadOnlyList<DateOnly>> pickedDays;
    private readonly Action<string, Action> added;
    private readonly Action<DateOnly, DateOnly, string>? openEvent;
    private IReadOnlyList<DateOnly> days = [];
    private ComposerDraft draft;
    private bool isEvent;

    /// <param name="pickedDays">The days the bar adds to, in the order they were picked.</param>
    /// <param name="added">Shows the undo for what was just added: its text, and what takes it all back.</param>
    /// <param name="openEvent">Opens the event editor on the days given with a title; null where there are no events.</param>
    public CalendarComposerViewModel(
        TaskList tasks,
        EventList? events,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Func<IReadOnlyList<DateOnly>> pickedDays,
        Action<string, Action> added,
        Action<Action> runOnUi,
        Action<DateOnly, DateOnly, string>? openEvent = null,
        ChatViewModel? chat = null)
        : base(strings, chat)
    {
        this.tasks = tasks;
        this.events = events;
        this.areas = areas;
        this.tags = tags;
        this.projects = projects;
        this.settings = settings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.pickedDays = pickedDays;
        this.added = added;
        this.openEvent = openEvent;
        draft = Parse(string.Empty);
        areas.Changed += (_, _) => runOnUi(RefreshPreview);
        tags.Changed += (_, _) => runOnUi(RefreshPreview);
        DaysChanged();
    }

    /// <summary>Whether the bar can add events here, so it shows the Task or Event switch.</summary>
    public bool HasEvents => events is not null;

    /// <summary>
    /// Whether a line becomes an event (one across the picked days) rather than a task (one on each).
    /// Only choosing counts: a radio button that its group unchecks says nothing.
    /// </summary>
    public bool IsEvent
    {
        get => isEvent;
        set
        {
            if (value)
            {
                Choose(HasEvents);
            }
        }
    }

    /// <summary>The other side of the switch, for its second button.</summary>
    public bool IsTask
    {
        get => !isEvent;
        set
        {
            if (value)
            {
                Choose(false);
            }
        }
    }

    /// <summary>Whether several days are picked, so the bar asks which to make.</summary>
    public bool IsSeveral => days.Count > 1;

    /// <summary>The Task or Event switch, for one picked day.</summary>
    public bool ShowsSwitch => HasEvents && !IsSeveral;

    /// <summary>The choice between one event and a task on each day, for several.</summary>
    public bool ShowsChoice => HasEvents && IsSeveral;

    /// <summary>Where a line lands: the picked day ("Mon 12 Oct"), or how many are picked ("3 days").</summary>
    public string DaysText => IsSeveral
        ? Strings.Get("Calendar.BarDays", PickedTaskDays().Count)
        : days.Count == 1 ? days[0].ToString("ddd d MMM", CultureInfo.CurrentCulture) : string.Empty;

    /// <summary>The same, as a screen reader says it.</summary>
    public string DaysName => IsSeveral
        ? Strings.Get("Calendar.BarDaysName", PickedTaskDays().Count)
        : days.Count == 1 ? Strings.Get("Calendar.BarDayName", days[0].ToString("D", CultureInfo.CurrentCulture)) : string.Empty;

    public override string FormName => Strings.Get("Event.Add");

    protected override string ItemPlaceholder => Strings.Get(IsEvent ? "Calendar.BarEventPlaceholder" : "Composer.Placeholder");

    protected override string AddName => (IsEvent, IsSeveral) switch
    {
        (true, _) => Strings.Get("Calendar.BarAddEvent"),
        (false, true) => Strings.Get("Calendar.BarAddTasks", PickedTaskDays().Count),
        _ => Strings.Get("Composer.Add"),
    };

    /// <summary>The page calls this after the picked days changed: the count, the switch and the preview follow.</summary>
    public void DaysChanged()
    {
        var wasSeveral = IsSeveral;
        days = pickedDays();
        if (IsSeveral && !wasSeveral)
        {
            // A pick starts at the choice used last on this PC.
            SetEvent(HasEvents && settings.PickedDaysAdd == PickedDaysAdd.OneEvent);
        }
        else if (!IsSeveral && wasSeveral)
        {
            SetEvent(false);
        }

        OnPropertyChanged(nameof(IsSeveral));
        OnPropertyChanged(nameof(ShowsSwitch));
        OnPropertyChanged(nameof(ShowsChoice));
        OnPropertyChanged(nameof(DaysText));
        OnPropertyChanged(nameof(DaysName));
        Announce();
    }

    protected override void OnLineEdited() => draft = Parse(Line);

    protected override bool CanAdd() =>
        days.Count > 0 && (IsEvent ? Line.Trim().Length > 0 && EventRules.PickedEvent(days) is not null : draft.Title.Trim().Length > 0 && draft.Command is null);

    protected override bool Add() => IsEvent ? AddEvent() : AddTasks();

    protected override void OpenFormWith(string text)
    {
        if (openEvent is not null && EventRules.PickedEvent(days) is var (first, last))
        {
            openEvent(first, last, text);
            ClearIf(text);
        }
    }

    protected override IEnumerable<ComposerChipViewModel> BuildChips()
    {
        if (!IsEvent)
        {
            return ComposerChips.Build(Line, Copied(), Today(), areas.All(), tags.Names(), projects.All(), Strings, areaBrush, Remove);
        }

        if (Line.Trim().Length == 0 || days.Count == 0)
        {
            return [];
        }

        return EventRules.PickedEvent(days) is var (first, last)
            ? [ComposerChipViewModel.Shown(EventText.Days(new EventItem(string.Empty, Line.Trim(), first, last), Strings), SymbolRegular.CalendarLtr24)]
            : [ComposerChipViewModel.Shown(Strings.Get("Event.TooLong", EventRules.MaxSpan), SymbolRegular.Warning24, warning: true)];
    }

    private bool AddEvent()
    {
        if (events is null || EventRules.PickedEvent(days) is not var (first, last) || events.Add(new EventDraft(Line, first, last)) is not { } item)
        {
            return false;
        }

        Remember(PickedDaysAdd.OneEvent);
        added(Strings.Get("Calendar.Added", item.Title), () => events.Delete(item.Id));
        return true;
    }

    private bool AddTasks()
    {
        if (!IsSeveral)
        {
            // One day plans the task for it, unless the line names a day of its own, as Tomorrow does.
            var day = days[0];
            if (tasks.Add(draft.PlannedDate is null ? draft with { PlannedDate = day } : draft) is not { } task)
            {
                return false;
            }

            added(Strings.Get("Calendar.Added", task.Title), () => tasks.Delete(task.Id));
            return true;
        }

        // A copy on each picked day, whatever day the line names.
        var copies = PickedTaskDays().Select(day => tasks.Add(Copied() with { PlannedDate = day })).OfType<TaskItem>().ToList();
        if (copies.Count == 0)
        {
            return false;
        }

        Remember(PickedDaysAdd.TaskOnEachDay);
        added(
            Strings.Get("Calendar.AddedEach", copies[0].Title, copies.Count),
            () =>
            {
                foreach (var copy in copies)
                {
                    tasks.Delete(copy.Id);
                }
            });
        return true;
    }

    private void Remember(PickedDaysAdd choice)
    {
        if (IsSeveral && settings.PickedDaysAdd != choice)
        {
            settings.PickedDaysAdd = choice;
        }
    }

    private void Choose(bool toEvent)
    {
        if (toEvent != isEvent)
        {
            SetEvent(toEvent);
            Announce();
        }
    }

    private void SetEvent(bool value)
    {
        if (value == isEvent)
        {
            return;
        }

        isEvent = value;
        OnPropertyChanged(nameof(IsEvent));
        OnPropertyChanged(nameof(IsTask));
    }

    // Everything that reads the mode or the days: the line's hint, the button and the preview.
    private void Announce()
    {
        HasForm = IsEvent && openEvent is not null;
        OnPropertyChanged(nameof(Placeholder));
        OnPropertyChanged(nameof(SendName));
        OnPropertyChanged(nameof(ButtonName));
        RefreshPreview();
    }

    // On several days each copy is a plain task on its day: a repeat the line names is dropped, and its chip with it.
    private ComposerDraft Copied() => IsSeveral ? draft with { Repeat = null } : draft;

    private IReadOnlyList<DateOnly> PickedTaskDays() => EventRules.PickedTaskDays(days);

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    private ComposerDraft Parse(string text) => ComposerParser.Parse(text, time.GetLocalNow().DateTime, settings.DayStartHour);

    private void Remove(ComposerChipViewModel chip) => Line = ComposerChips.RemoveParts(Line, chip.Spans);
}

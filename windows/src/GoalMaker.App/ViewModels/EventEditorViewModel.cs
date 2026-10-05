using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Edits a calendar event over the calendar page, or adds one (docs/calendar.md): the title, the first
/// and the last day, the area and the notes. Save is offered only for what the server takes
/// (<see cref="EventRules.Check"/>); Delete closes the editor and leaves the undo to the page.
/// </summary>
public sealed partial class EventEditorViewModel : ObservableObject
{
    private readonly EventList events;
    private readonly AreaList areas;
    private readonly IStrings strings;
    private EventItem? editing;

    [ObservableProperty]
    private bool isOpen;

    [ObservableProperty]
    private string heading = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanDelete))]
    private bool isAdding;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private string title = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(Problem), nameof(HasProblem))]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private DateTime? firstDay;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(Problem), nameof(HasProblem))]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private DateTime? lastDay;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private string notes = string.Empty;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> areaChoices = [];

    [ObservableProperty]
    private ChoiceViewModel? area;

    public EventEditorViewModel(EventList events, AreaList areas, IStrings strings)
    {
        this.events = events;
        this.areas = areas;
        this.strings = strings;
    }

    /// <summary>The event Delete took away, for the page's undo.</summary>
    public event EventHandler<EventItem>? Deleted;

    /// <summary>An event that is already there can be deleted; a new one is only cancelled.</summary>
    public bool CanDelete => !IsAdding;

    /// <summary>Why the days can't be kept, or empty.</summary>
    public string Problem => (Day(FirstDay), Day(LastDay)) switch
    {
        ({ } first, { } last) when last < first => strings.Get("Event.EndBeforeStart"),
        ({ } first, { } last) when last.DayNumber - first.DayNumber > EventRules.MaxSpan => strings.Get("Event.TooLong", EventRules.MaxSpan),
        _ => string.Empty,
    };

    public bool HasProblem => Problem.Length > 0;

    /// <summary>Opens the editor on an event.</summary>
    public void Open(EventItem item)
    {
        editing = item;
        Fill(item.Title, item.StartsOn, item.EndsOn, item.Notes, item.AreaId);
    }

    /// <summary>Opens the editor blank, on the days given, maybe with a title already typed.</summary>
    public void OpenNew(DateOnly first, DateOnly last, string title = "")
    {
        editing = null;
        Fill(title, first, last, null, null);
    }

    /// <summary>Keeps the event; null when it could not be (not valid, or deleted meanwhile).</summary>
    public EventItem? Keep()
    {
        if (Draft() is not { } draft)
        {
            return null;
        }

        var kept = editing is null ? events.Add(draft) : events.Update(editing.Id, draft) ? events.Get(editing.Id) : null;
        if (kept is not null)
        {
            IsOpen = false;
        }

        return kept;
    }

    private static DateOnly? Day(DateTime? picked) => picked is { } day ? DateOnly.FromDateTime(day) : null;

    private void Fill(string text, DateOnly first, DateOnly last, string? notesText, string? areaId)
    {
        IsAdding = editing is null;
        Heading = strings.Get(IsAdding ? "Event.Add" : "Event.Edit");
        Title = text;
        FirstDay = first.ToDateTime(TimeOnly.MinValue);
        LastDay = last.ToDateTime(TimeOnly.MinValue);
        Notes = notesText ?? string.Empty;

        // No area and the areas not archived, and the event's own even when it is archived, so an edit never loses it.
        AreaChoices =
        [
            new ChoiceViewModel(null, strings.Get("Task.NoArea")),
            .. areas.All()
                .Where(item => !item.Archived || item.Id == areaId)
                .Select(item => new ChoiceViewModel(item.Id, item.Emoji is { } emoji ? $"{emoji} {item.Name}" : item.Name)),
        ];
        Area = AreaChoices.FirstOrDefault(choice => choice.Id == areaId) ?? AreaChoices[0];
        IsOpen = true;
    }

    private EventDraft? Draft() =>
        Day(FirstDay) is { } first && Day(LastDay) is { } last
            ? EventRules.Check(new EventDraft(Title, first, last, Notes, Area?.Id))
            : null;

    private bool CanSave() => Draft() is not null;

    [RelayCommand(CanExecute = nameof(CanSave))]
    private void Save() => Keep();

    [RelayCommand]
    private void Cancel() => IsOpen = false;

    [RelayCommand]
    private void Delete()
    {
        if (editing is not { } item || !events.Delete(item.Id))
        {
            return;
        }

        IsOpen = false;
        Deleted?.Invoke(this, item);
    }
}

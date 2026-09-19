using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Activity;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Activity page (docs/activity.md): recent changes by the owner and by Claude, read online, with
/// Undo on each row's latest change (the server refuses to undo a change the row has moved on from;
/// undoing the undo takes a mistaken one back). <c>requestSync</c> brings a restored row into the
/// replica right away.
/// </summary>
public sealed partial class ActivityViewModel : ObservableObject
{
    // Rows the server can put back (undo_activity in supabase/migrations/0008).
    private static readonly HashSet<string> Undoable =
        ["areas", "tags", "goals", "goal_entries", "tasks", "task_steps", "task_tags", "reminders", "ritual_runs", "reviews"];

    private readonly IActivityLog log;
    private readonly IStrings strings;
    private readonly Action requestSync;

    [ObservableProperty]
    private bool loaded;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowList))]
    private bool unavailable;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(RefreshCommand))]
    private bool busy;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasMessage))]
    private string message = string.Empty;

    public ActivityViewModel(IActivityLog log, IStrings strings, Action requestSync)
    {
        this.log = log;
        this.strings = strings;
        this.requestSync = requestSync;
    }

    public ObservableCollection<ActivityRowViewModel> Rows { get; } = [];

    public bool HasMessage => Message.Length > 0;

    public bool ShowList => !Unavailable;

    /// <summary>The entries as rows, newest first, with Undo on each row's latest change that stands.</summary>
    public static IReadOnlyList<(ActivityEntry Entry, bool Undoable)> Order(IEnumerable<ActivityEntry> entries)
    {
        var seen = new HashSet<(string, string)>();
        return [.. entries.OrderByDescending(entry => entry.Id)
            .Select(entry => (entry, seen.Add((entry.Entity, entry.EntityId)) && entry.UndoneAt is null && Undoable.Contains(entry.Entity)))];
    }

    [RelayCommand(CanExecute = nameof(CanRefresh))]
    public Task RefreshAsync() => CallAsync(LoadAsync);

    private bool CanRefresh() => !Busy;

    private Task UndoAsync(ActivityRowViewModel row) => CallAsync(async () =>
    {
        var outcome = await log.UndoAsync(row.Entry.Id);
        Message = strings.Get(outcome switch
        {
            UndoOutcome.Undone => "Activity.UndoDone",
            UndoOutcome.ChangedSince => "Activity.UndoChanged",
            UndoOutcome.AlreadyUndone => "Activity.UndoAlready",
            _ => "Activity.UndoImpossible",
        });
        if (outcome == UndoOutcome.Undone)
        {
            requestSync();
        }

        await LoadAsync();
    });

    private async Task LoadAsync()
    {
        var entries = await log.RecentAsync();
        Rows.Clear();
        foreach (var (entry, undoable) in Order(entries))
        {
            Rows.Add(new ActivityRowViewModel(entry, Sentence(entry), When(entry), undoable, UndoAsync));
        }

        IsEmpty = Rows.Count == 0;
        Unavailable = false;
        Loaded = true;
    }

    private async Task CallAsync(Func<Task> work)
    {
        if (Busy)
        {
            return;
        }

        Busy = true;
        try
        {
            await work();
        }
        catch (Exception error) when (error is RemoteUnavailableException or RemoteRejectedException)
        {
            Unavailable = true;
            Loaded = true;
        }
        finally
        {
            Busy = false;
        }
    }

    private string When(ActivityEntry entry)
    {
        var at = entry.CreatedAt.ToLocalTime().ToString("ddd d MMM, HH:mm", CultureInfo.CurrentCulture);
        return entry.UndoneAt is null ? at : $"{at} · {strings.Get("Activity.Undone")}";
    }

    /// <summary>One change in plain words: "Claude moved “Call the bank” to Mon 21 Sep".</summary>
    private string Sentence(ActivityEntry entry)
    {
        var change = entry.Change;
        var actor = strings.Get(entry.Actor switch
        {
            "claude" => "Activity.ActorClaude",
            "system" => "Activity.ActorSystem",
            _ => "Activity.ActorOwner",
        });
        var subject = change.Subject ?? string.Empty;
        var what = entry.Entity switch
        {
            "tasks" => strings.Get("Activity.Task", subject),
            "task_steps" => strings.Get("Activity.Step", subject),
            "areas" => strings.Get("Activity.Area", subject),
            "tags" => strings.Get("Activity.Tag", subject),
            "reminders" => strings.Get("Activity.Reminder"),
            "task_tags" => strings.Get("Activity.TagLink"),
            "ritual_runs" => strings.Get("Activity.Ritual"),
            "reviews" => strings.Get("Activity.Review"),
            "goals" => strings.Get("Activity.Goal", subject),
            "goal_entries" => strings.Get("Activity.GoalEntry"),
            _ => entry.Entity,
        };
        return change.Change switch
        {
            "moved" when change.Day is { } day => strings.Get(
                "Activity.Moved",
                actor,
                what,
                DateOnly.ParseExact(day, "yyyy-MM-dd", CultureInfo.InvariantCulture).ToString("ddd d MMM", CultureInfo.CurrentCulture)),
            "moved" => strings.Get("Activity.MovedOff", actor, what),
            _ => strings.Get("Activity." + CultureInfo.InvariantCulture.TextInfo.ToTitleCase(change.Change), actor, what),
        };
    }
}

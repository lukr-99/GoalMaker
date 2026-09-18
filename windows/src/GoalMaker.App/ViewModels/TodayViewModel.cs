using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Today, M1 version: the open tasks from the replica, the composer adding tasks by title, and the
/// sync status. M2 replaces the list with the real Today (days, priorities, habits).
/// </summary>
public sealed partial class TodayViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly IStrings strings;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTaskCommand))]
    private string newTaskTitle = string.Empty;

    [ObservableProperty]
    private string greeting = string.Empty;

    [ObservableProperty]
    private string syncText = string.Empty;

    [ObservableProperty]
    private bool isEmpty = true;

    public TodayViewModel(TaskList tasks, SyncCoordinator sync, IAuthGateway auth, IStrings strings, Action<Action> runOnUi)
    {
        this.tasks = tasks;
        this.strings = strings;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        sync.StatusChanged += (_, status) => runOnUi(() => ShowSync(status));
        auth.SessionChanged += (_, session) => runOnUi(() => ShowSession(session));
        ShowSession(auth.Session);
        ShowSync(sync.Status);
        Refresh();
    }

    public ObservableCollection<TaskRowViewModel> Tasks { get; } = [];

    private bool CanAddTask() => !string.IsNullOrWhiteSpace(NewTaskTitle);

    [RelayCommand(CanExecute = nameof(CanAddTask))]
    private void AddTask()
    {
        if (tasks.Add(NewTaskTitle) is not null)
        {
            NewTaskTitle = string.Empty;
        }
    }

    private void Refresh()
    {
        Tasks.Clear();
        foreach (var item in tasks.Open())
        {
            Tasks.Add(new TaskRowViewModel(item, tasks));
        }

        IsEmpty = Tasks.Count == 0;
    }

    private void ShowSession(AuthSession session) =>
        Greeting = session is AuthSession.SignedIn signedIn ? strings.Get("Today.Greeting", signedIn.Email) : string.Empty;

    private void ShowSync(SyncStatus status) => SyncText = status switch
    {
        { State: SyncState.Syncing } => strings.Get("Sync.Syncing"),
        { State: SyncState.Offline } => strings.Get("Sync.Offline", status.PendingChanges),
        { State: SyncState.NeedsAttention } => strings.Get("Sync.NeedsAttention", status.Problem ?? string.Empty),
        { LastSyncedAt: { } at } => strings.Get("Sync.SyncedAt", at.ToLocalTime().ToString("t", CultureInfo.CurrentCulture)),
        _ => string.Empty,
    };
}

using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Today: the open tasks from the replica, the composer with its live preview (docs/composer.md),
/// and the sync status. M2-06 replaces the list with the real Today (days, priorities, habits).
/// </summary>
public sealed partial class TodayViewModel : ObservableObject
{
    /// <summary>The planning day starts at 04:00 (spec, story 25); a setting arrives with M2-06.</summary>
    private const int RolloverHour = 4;

    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private ComposerDraft draft = ComposerParser.Parse(string.Empty, DateTime.Now);

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTaskCommand))]
    private string newTaskTitle = string.Empty;

    [ObservableProperty]
    private string greeting = string.Empty;

    [ObservableProperty]
    private string syncText = string.Empty;

    [ObservableProperty]
    private bool isEmpty = true;

    [ObservableProperty]
    private bool hasChips;

    public TodayViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        SyncCoordinator sync,
        IAuthGateway auth,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Action<Action> runOnUi)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(UpdatePreview);
        tags.Changed += (_, _) => runOnUi(UpdatePreview);
        sync.StatusChanged += (_, status) => runOnUi(() => ShowSync(status));
        auth.SessionChanged += (_, session) => runOnUi(() => ShowSession(session));
        ShowSession(auth.Session);
        ShowSync(sync.Status);
        Refresh();
    }

    public ObservableCollection<TaskRowViewModel> Tasks { get; } = [];

    /// <summary>What the line will save, as it's typed.</summary>
    public ObservableCollection<ComposerChipViewModel> Chips { get; } = [];

    partial void OnNewTaskTitleChanged(string value) => UpdatePreview();

    private bool CanAddTask() => draft.Title.Trim().Length > 0 && draft.Command is null;

    [RelayCommand(CanExecute = nameof(CanAddTask))]
    private void AddTask()
    {
        if (tasks.Add(draft) is not null)
        {
            NewTaskTitle = string.Empty;
        }
    }

    private void UpdatePreview()
    {
        var now = time.GetLocalNow().DateTime;
        draft = ComposerParser.Parse(NewTaskTitle, now, RolloverHour);
        var today = DateOnly.FromDateTime(now.AddHours(-RolloverHour));
        Chips.Clear();
        foreach (var chip in ComposerChips.Build(NewTaskTitle, draft, today, areas.All(), tags.Names(), strings, areaBrush, Remove))
        {
            Chips.Add(chip);
        }

        HasChips = Chips.Count > 0;
        AddTaskCommand.NotifyCanExecuteChanged();
    }

    private void Remove(ComposerChipViewModel chip) => NewTaskTitle = ComposerChips.RemoveParts(NewTaskTitle, chip.Spans);

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

using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>The archive of done tasks (docs/archive.md): searched as you type, newest first.</summary>
public sealed partial class ArchiveViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly IStrings strings;
    private readonly Action<string> openTask;

    [ObservableProperty]
    private string query = string.Empty;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string emptyText = string.Empty;

    public ArchiveViewModel(TaskList tasks, IStrings strings, Action<Action> runOnUi, Action<string> openTask)
    {
        this.tasks = tasks;
        this.strings = strings;
        this.openTask = openTask;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public ObservableCollection<ArchiveRowViewModel> Results { get; } = [];

    public void Refresh()
    {
        Results.Clear();
        foreach (var task in ArchiveRules.Search(tasks.All(), Query))
        {
            var done = task.CompletedAt is { } stamp && SyncRules.InstantOf(stamp) is { } instant
                ? strings.Get("Archive.DoneOn", instant.ToLocalTime().ToString("ddd d MMM", CultureInfo.CurrentCulture))
                : string.Empty;
            Results.Add(new ArchiveRowViewModel(task.Title, done, new RelayCommand(() => openTask(task.Id)), new RelayCommand(() => tasks.SetDone(task.Id, false))));
        }

        IsEmpty = Results.Count == 0;
        EmptyText = strings.Get(Query.Trim().Length == 0 ? "Archive.Empty" : "Archive.NoMatch");
    }

    partial void OnQueryChanged(string value) => Refresh();
}

using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The archive of done tasks (docs/archive.md): searched as you type, newest first, and narrowed by an
/// area and tag filter of its own, the same filter the lists use.
/// </summary>
public sealed partial class ArchiveViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ListFilterState filter = new();
    private readonly IStrings strings;
    private readonly Action<string> openTask;

    [ObservableProperty]
    private string query = string.Empty;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string emptyText = string.Empty;

    public ArchiveViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        IStrings strings,
        Func<string, Brush?> areaBrush,
        Action<Action> runOnUi,
        Action<string> openTask)
    {
        this.tasks = tasks;
        this.tags = tags;
        this.projects = projects;
        this.strings = strings;
        this.openTask = openTask;
        Filters = new ListFiltersViewModel(areas, tags, filter, strings, areaBrush, runOnUi);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        projects.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public ObservableCollection<ArchiveRowViewModel> Results { get; } = [];

    /// <summary>The area and tag pickers under the search box.</summary>
    public ListFiltersViewModel Filters { get; }

    public void Refresh()
    {
        Results.Clear();
        var found = ArchiveRules.Search(tasks.All(), Query);
        if (!filter.Current.IsEmpty)
        {
            found = filter.Current.Apply(found, tags.TagLinks(), projects.All().ToDictionary(project => project.Id, project => project.AreaId, StringComparer.Ordinal));
        }

        foreach (var task in found)
        {
            var done = task.CompletedAt is { } stamp && SyncRules.InstantOf(stamp) is { } instant
                ? strings.Get("Archive.DoneOn", instant.ToLocalTime().ToString("ddd d MMM", CultureInfo.CurrentCulture))
                : string.Empty;
            Results.Add(new ArchiveRowViewModel(task.Title, done, new RelayCommand(() => openTask(task.Id)), new RelayCommand(() => tasks.SetDone(task.Id, false))));
        }

        IsEmpty = Results.Count == 0;
        EmptyText = strings.Get(Query.Trim().Length == 0 && filter.Current.IsEmpty ? "Archive.Empty" : "Archive.NoMatch");
    }

    partial void OnQueryChanged(string value) => Refresh();
}

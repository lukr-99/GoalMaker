using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The All tasks page (docs/all-tasks.md): every open task, grouped by area with the no-area group last,
/// searched as you type and narrowed by an area and tag filter of its own, so a task filed into an area without
/// a day can always be found. Project items show only when asked for.
/// </summary>
public sealed partial class AllTasksViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly Func<string, Brush?> areaBrush;
    private readonly TimeProvider time;
    private readonly Action<string> openTask;
    private readonly Action<string>? openProject;
    private readonly ListFilterState filter = new();

    [ObservableProperty]
    private string query = string.Empty;

    /// <summary>Whether project items show too; off by default, since their boards already list them.</summary>
    [ObservableProperty]
    private bool showProjectItems;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string emptyText = string.Empty;

    public AllTasksViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        Func<string, Brush?> areaBrush,
        TimeProvider time,
        Action<Action> runOnUi,
        Action<string> openTask,
        Action<string>? openProject = null)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.projects = projects;
        this.settings = settings;
        this.strings = strings;
        this.areaBrush = areaBrush;
        this.time = time;
        this.openTask = openTask;
        this.openProject = openProject;
        Filters = new ListFiltersViewModel(areas, tags, filter, strings, areaBrush, runOnUi);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        projects.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public ObservableCollection<AllTasksGroupViewModel> Groups { get; } = [];

    /// <summary>The area and tag pickers under the search box.</summary>
    public ListFiltersViewModel Filters { get; }

    public void Refresh()
    {
        Groups.Clear();
        var areaList = areas.All();
        var areaById = areaList.ToDictionary(area => area.Id, StringComparer.Ordinal);
        var projectList = projects.All();
        var projectById = projectList.ToDictionary(project => project.Id, StringComparer.Ordinal);
        var projectAreas = projectList.ToDictionary(project => project.Id, project => project.AreaId, StringComparer.Ordinal);
        IReadOnlyList<TaskItem> all = tasks.All();
        if (!filter.Current.IsEmpty)
        {
            all = filter.Current.Apply(all, tags.TagLinks(), projectAreas);
        }

        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        foreach (var group in AllTasksRules.Groups(all, [.. areaList.Select(area => area.Id)], projectAreas, Query, ShowProjectItems))
        {
            var area = group.AreaId is { } id ? areaById.GetValueOrDefault(id) : null;
            var name = area is null ? strings.Get("AllTasks.NoArea") : area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name;
            Groups.Add(new AllTasksGroupViewModel(
                name,
                area is null ? null : areaBrush(area.ColorId),
                [.. group.Tasks.Select(task => Row(task, today, projectById))]));
        }

        IsEmpty = Groups.Count == 0;
        EmptyText = strings.Get(Query.Trim().Length == 0 && filter.Current.IsEmpty ? "AllTasks.Empty" : "AllTasks.NoMatch");
    }

    private AllTasksRowViewModel Row(TaskItem task, DateOnly today, IReadOnlyDictionary<string, ProjectItem> projectById)
    {
        var date = task.Deadline ?? task.PlannedDate;
        var dateText = date is not { } day
            ? string.Empty
            : task.Deadline is not null
                ? strings.Get("AllTasks.Due", day.ToString("ddd d MMM", CultureInfo.CurrentCulture))
                : day.ToString("ddd d MMM", CultureInfo.CurrentCulture);
        return new AllTasksRowViewModel(
            task.Title,
            dateText,
            date is { } when && when < today,
            new RelayCommand(() => openTask(task.Id)),
            new RelayCommand(() => tasks.SetDone(task.Id, true)),
            ProjectTagViewModel.For(task, projectById, strings, openProject));
    }

    partial void OnQueryChanged(string value) => Refresh();

    partial void OnShowProjectItemsChanged(bool value) => Refresh();
}

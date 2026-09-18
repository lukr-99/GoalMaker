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
/// The tray's Today flyout (spec, story 79): today's open tasks in the list's order, overdue first,
/// with their done boxes, and ways to add a task or open GoalMaker. Checking a task finishes it right
/// away; the full list offers undo, the flyout keeps to a glance.
/// </summary>
public sealed partial class TrayFlyoutViewModel : ObservableObject
{
    /// <summary>How many tasks the flyout lists before it only counts the rest.</summary>
    public const int MaxRows = 8;

    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;

    [ObservableProperty]
    private string subtitle = string.Empty;

    [ObservableProperty]
    private string moreText = string.Empty;

    [ObservableProperty]
    private bool hasMore;

    [ObservableProperty]
    private bool isEmpty;

    public TrayFlyoutViewModel(
        TaskList tasks,
        AreaList areas,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Action<Action> runOnUi,
        Action openApp,
        Action quickAdd)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        OpenAppCommand = new RelayCommand(openApp);
        QuickAddCommand = new RelayCommand(quickAdd);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public string Title => strings.Get("Lists.Today");

    public string EmptyText => strings.Get("Tray.Empty");

    public ObservableCollection<TaskRowViewModel> Rows { get; } = [];

    public IRelayCommand OpenAppCommand { get; }

    public IRelayCommand QuickAddCommand { get; }

    /// <summary>Reads today again; the tray calls it each time the flyout opens, in case the day moved on.</summary>
    public void Refresh()
    {
        var lists = ListRules.Lists(tasks.All(), PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour));
        var sections = lists.TodaySections;
        var open = sections.Overdue.Concat(sections.Priorities).Concat(sections.Scheduled).Concat(sections.More).ToList();
        var areaById = areas.All().ToDictionary(area => area.Id, StringComparer.Ordinal);
        var overdue = sections.Overdue.Select(task => task.Id).ToHashSet(StringComparer.Ordinal);

        Rows.Clear();
        foreach (var task in open.Take(MaxRows))
        {
            var area = task.AreaId is { } id ? areaById.GetValueOrDefault(id) : null;
            Rows.Add(new TaskRowViewModel(task, area, area is null ? null : areaBrush(area.ColorId), overdue.Contains(task.Id), Complete, _ => { }));
        }

        var date = lists.Today.ToString("dddd d MMMM", CultureInfo.CurrentCulture);
        Subtitle = lists.Summary.Total == 0 ? date : strings.Get("Lists.TodaySummary", date, lists.Summary.Done, lists.Summary.Total);
        HasMore = open.Count > MaxRows;
        MoreText = HasMore ? strings.Get("Tray.More", open.Count - MaxRows) : string.Empty;
        IsEmpty = open.Count == 0;
    }

    private void Complete(TaskRowViewModel row) => tasks.SetDone(row.Item.Id, true);
}

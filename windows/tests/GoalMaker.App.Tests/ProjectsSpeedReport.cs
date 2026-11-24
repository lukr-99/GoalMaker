using System.Diagnostics;
using System.Windows;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;

namespace GoalMaker.App.Tests;

/// <summary>
/// How long the Projects page takes on <see cref="LargeBoard"/>, on the UI thread, with the page laid
/// out in a window offscreen: opening the tab, typing in the board's search, the area filter, the
/// who-made-it switch, switching projects and a change arriving from sync. Explicit, since it measures
/// this PC: run with <c>dotnet test --project tests/GoalMaker.App.Tests -- --explicit only
/// --filter-class *ProjectsSpeedReport</c> and read the numbers in the output (GM-121).
/// </summary>
[Collection(nameof(WpfCollection))]
public sealed class ProjectsSpeedReport(ITestOutputHelper output)
{
    [Fact(Explicit = true)]
    public void Report() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        var fill = Stopwatch.StartNew();
        LargeBoard.Fill(planner);
        Write($"fixture: {fill.ElapsedMilliseconds} ms, {planner.Tasks.All().Count} tasks, {planner.Projects.All().Count} projects");
        var big = LargeBoard.Big(planner);
        Print("read every task from the replica", Enumerable.Range(0, 5).Select(_ => Time(() => planner.Tasks.All())));
        Print("read the tag links", Enumerable.Range(0, 5).Select(_ => Time(() => planner.Tags.TagLinks())));
        Print("read the projects", Enumerable.Range(0, 5).Select(_ => Time(() => planner.Projects.All())));
        var every = planner.Tasks.All();
        Print("fold every title and notes", Enumerable.Range(0, 5).Select(_ => Time(() => every.Select(task => Core.Planning.ArchiveRules.Fold(task.Title) + Core.Planning.ArchiveRules.Fold(task.Notes)).ToList())));

        var alone = new ProjectsViewModel(
            planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, new ResourceStrings(Application.Current), _ => null, _ => { }, action => action(), planner.Time, _ => { });
        var ids = planner.Projects.All().Select(project => project.Id).Take(6).Append(big.Id).ToList();
        Print("switch project, the view model alone", ids.Concat(ids).Select(id => Time(() => alone.Select(id))));
        Print("type in the board search, the view model alone", "sync widget".Select((_, i) => Time(() =>
        {
            alone.BoardQuery = "sync widget"[..(i + 1)];
            ProjectsSpeed.Flush(planner, alone);
        })));

        ProjectsViewModel? projects = null;
        ProjectsPage? page = null;
        System.Windows.Interop.HwndSource? window = null;
        var open = Time(() =>
        {
            projects = new ProjectsViewModel(
                planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, new ResourceStrings(Application.Current), _ => null, _ => { }, action => action(), planner.Time, _ => { });
            projects.Select(big.Id);
            page = new ProjectsPage(projects);
            window = TabOrder.Host(page, 1280, 860);
        });
        Write($"open the tab on {LargeBoard.BigProject}: {open:F1} ms, {Count<System.Windows.Controls.ListBoxItem>(page!)} cards drawn of {projects!.Columns.Sum(column => column.Items.Count)}");
        using (window)
        {
            var vm = projects!;
            var modelTimes = new List<double>();
            void Settled(Action change)
            {
                modelTimes.Add(Time(() =>
                {
                    change();
                    ProjectsSpeed.Flush(planner, vm);
                }));
                page!.UpdateLayout();
            }

            Print("type 'sync widget' in the board search", "sync widget".Select((_, i) => Time(() => Settled(() => vm.BoardQuery = "sync widget"[..(i + 1)]))), modelTimes);
            Print("clear the board search", [Time(() => Settled(() => vm.BoardQuery = string.Empty))], modelTimes);
            Print("area filter on and off", Enumerable.Range(0, 6).Select(i => Time(() => Settled(() => vm.Filters.SelectedArea = vm.Filters.AreaChoices[i % 2 == 0 ? 1 : 0]))), modelTimes);
            Print("who made it: Claude, everyone", Enumerable.Range(0, 6).Select(i => Time(() => Settled(() => vm.MadeByFilter = i % 2 == 0 ? "claude" : "all"))), modelTimes);
            var others = planner.Projects.All().Where(project => project.Id != big.Id).Take(5).Select(project => project.Id).ToList();
            Print("switch project", others.Append(big.Id).Select(id => Time(() => Settled(() => vm.Select(id)))), modelTimes);
            var item = planner.Tasks.All().First(task => task.ProjectId == big.Id && task.State == Core.Planning.TaskState.Open);
            Print("a change from sync (priority of one item)", Enumerable.Range(0, 6).Select(i => Time(() => Settled(() => planner.Tasks.SetPriority(item.Id, i % 2 == 0 ? "high" : "low")))), modelTimes);
            // As in the app: the real clock, so the read runs on a pool thread, and the UI thread's part
            // timed where the work hops onto it.
            var dispatcher = System.Windows.Threading.Dispatcher.CurrentDispatcher;
            var onUi = new List<double>();
            var live = new ProjectsViewModel(
                planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, new ResourceStrings(Application.Current), _ => null, _ => { },
                action =>
                {
                    void Timed() => onUi.Add(Time(action));
                    if (dispatcher.CheckAccess())
                    {
                        Timed();
                    }
                    else
                    {
                        dispatcher.Invoke(Timed);
                    }
                },
                TimeProvider.System,
                _ => { });
            live.Select(big.Id);
            var livePage = new ProjectsPage(live);
            using (TabOrder.Host(livePage, 1280, 860))
            {
                vm.BoardQuery = "zzzz";
                ProjectsSpeed.Flush(planner, vm);
                Print("a change from sync, the UI thread's part with the read off it", Enumerable.Range(0, 6).Select(i =>
                {
                    onUi.Clear();
                    planner.Tasks.SetPriority(item.Id, i % 2 == 0 ? "high" : "low");
                    var until = DateTime.UtcNow.AddSeconds(2);
                    while (onUi.Count < 2 && DateTime.UtcNow < until)
                    {
                        TabOrder.Settle();
                    }

                    livePage.UpdateLayout();
                    return onUi.Count < 2 ? double.NaN : onUi.Sum();
                }));
                vm.BoardQuery = string.Empty;
                ProjectsSpeed.Flush(planner, vm);
            }

            livePage.DataContext = null;
            Print("come back to the tab (the page is kept)", Enumerable.Range(0, 5).Select(_ =>
            {
                // Away to another page and back: Loaded runs again, as it does in the app.
                window!.RootVisual = null;
                TabOrder.Settle();
                return Time(() =>
                {
                    window.RootVisual = page;
                    page!.UpdateLayout();
                    TabOrder.Settle();
                });
            }));
            Print("open the tab again, warm", Enumerable.Range(0, 5).Select(_ => Time(() => Open(vm))));
            Print("  with an empty board and list", Enumerable.Range(0, 5).Select(_ =>
            {
                vm.BoardQuery = "zzzz";
                vm.PaneQuery = "zzzz";
                ProjectsSpeed.Flush(planner, vm);
                var took = Time(() => Open(vm));
                vm.BoardQuery = string.Empty;
                vm.PaneQuery = string.Empty;
                ProjectsSpeed.Flush(planner, vm);
                return took;
            }));
            Print("  an empty window, for scale", Enumerable.Range(0, 5).Select(_ => Time(() =>
            {
                using var second = TabOrder.Host(new System.Windows.Controls.TextBlock { Text = "x" }, 1280, 860);
            })));
        }
    });

    // The test output, and a file beside it (GOALMAKER_SPEED, or goalmaker-speed.txt in the temp folder) to compare runs.
    private void Write(string line)
    {
        output.WriteLine(line);
        var file = Environment.GetEnvironmentVariable("GOALMAKER_SPEED") ?? System.IO.Path.Combine(System.IO.Path.GetTempPath(), "goalmaker-speed.txt");
        System.IO.File.AppendAllText(file, line + Environment.NewLine);
    }

    // A page drawn in a window and let go again, so it stops following the view model.
    private static void Open(ProjectsViewModel vm)
    {
        var page = new ProjectsPage(vm);
        using (TabOrder.Host(page, 1280, 860))
        {
        }

        page.DataContext = null;
    }

    private static int Count<T>(System.Windows.DependencyObject node)
    {
        var count = node is T ? 1 : 0;
        for (var i = 0; i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(node); i++)
        {
            count += Count<T>(System.Windows.Media.VisualTreeHelper.GetChild(node, i));
        }

        return count;
    }

    private static double Time(Action action)
    {
        var watch = Stopwatch.StartNew();
        action();
        return watch.Elapsed.TotalMilliseconds;
    }

    private void Print(string what, IEnumerable<double> times, List<double>? model = null)
    {
        model?.Clear();
        var list = times.ToList();
        var sorted = list.Order().ToList();
        var line = $"{what}: median {sorted[sorted.Count / 2]:F1} ms, worst {sorted[^1]:F1} ms ({list.Count} runs)";
        if (model is { Count: > 0 })
        {
            var inModel = model.Order().ToList();
            line += $"; the view model's share: median {inModel[inModel.Count / 2]:F1} ms";
        }

        Write(line);
    }
}

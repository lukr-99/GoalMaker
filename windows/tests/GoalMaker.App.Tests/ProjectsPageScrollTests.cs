using System.Windows;
using System.Windows.Controls;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The project list beside the board scrolls (GM-94): with more projects than the window is tall,
/// the rows sit in a scroll viewer that can move, instead of running off the bottom of the page.
/// </summary>
[Collection(nameof(WpfCollection))]
public sealed class ProjectsPageScrollTests
{
    [Fact]
    public void AListLongerThanTheWindowScrolls() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        for (var i = 1; i <= 40; i++)
        {
            planner.Projects.Add(new ProjectDraft($"Project {i}"));
        }

        var projects = new ProjectsViewModel(
            planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, new ResourceStrings(Application.Current), _ => null, _ => { }, action => action(), planner.Time, _ => { });
        projects.Refresh();
        var page = new ProjectsPage(projects);
        using var window = TabOrder.Host(page, 1100, 600);

        var list = (ScrollViewer)page.FindName("ProjectListScroller");

        Assert.True(list.ScrollableHeight > 0, "The project rows must be taller than their viewer, so it can scroll.");
        Assert.True(list.ActualHeight <= 600, "The viewer must fit the page, not grow to the height of its rows.");
        list.ScrollToEnd();
        list.UpdateLayout();
        Assert.True(list.VerticalOffset > 0);
    });

    [Fact]
    public void TheWheelOverTheBoardScrollsThePage() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        var projects = new ProjectsViewModel(
            planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, new ResourceStrings(Application.Current), _ => null, _ => { }, action => action(), planner.Time, _ => { });
        projects.NewCommand.Execute(null);
        projects.ProjectName = "Tall";
        projects.SaveCommand.Execute(null);
        for (var i = 1; i <= 40; i++)
        {
            projects.NewItemTitle = $"Item {i}";
            projects.AddItemCommand.Execute(null);
        }

        var page = new ProjectsPage(projects);
        using var window = TabOrder.Host(page, 1100, 600);
        var board = FindBoard(page);
        DependencyObject up = board;
        do
        {
            up = System.Windows.Media.VisualTreeHelper.GetParent(up);
        }
        while (up is not ScrollViewer);
        var outer = (ScrollViewer)up;

        Assert.True(outer.ScrollableHeight > 0);
        board.RaiseEvent(new System.Windows.Input.MouseWheelEventArgs(System.Windows.Input.Mouse.PrimaryDevice, Environment.TickCount, -240)
        {
            RoutedEvent = UIElement.PreviewMouseWheelEvent,
        });
        outer.UpdateLayout();

        Assert.True(outer.VerticalOffset > 0, "The wheel over the board must scroll the page.");
    });

    private static ScrollViewer FindBoard(DependencyObject root)
    {
        var queue = new Queue<DependencyObject>([root]);
        while (queue.Count > 0)
        {
            var node = queue.Dequeue();
            if (node is ScrollViewer { VerticalScrollBarVisibility: ScrollBarVisibility.Disabled } viewer)
            {
                return viewer;
            }

            for (var i = 0; i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(node); i++)
            {
                queue.Enqueue(System.Windows.Media.VisualTreeHelper.GetChild(node, i));
            }
        }

        throw new InvalidOperationException("No board scroll viewer.");
    }
}

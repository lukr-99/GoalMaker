using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The project list beside the board scrolls (GM-94): with more projects than the window is tall, the
/// rows sit in a scroll viewer that can move, instead of running off the bottom of the page. A long
/// column scrolls on its own and draws only the cards in view (GM-121).
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

        var projects = Page(planner);
        projects.Refresh();
        var page = new ProjectsPage(projects);
        using var window = TabOrder.Host(page, 1100, 600);

        var list = Find<ScrollViewer>((ItemsControl)page.FindName("ProjectList"));

        Assert.True(list.ScrollableHeight > 0, "The project rows must be taller than their viewer, so it can scroll.");
        Assert.True(list.ActualHeight <= 600, "The viewer must fit the page, not grow to the height of its rows.");
        list.ScrollToEnd();
        list.UpdateLayout();
        Assert.True(list.VerticalOffset > 0);
    });

    [Fact]
    public void ALongColumnScrollsOnItsOwnAndDrawsOnlyTheCardsInView() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        var projects = Page(planner);
        projects.NewCommand.Execute(null);
        projects.ProjectName = "Tall";
        projects.SaveCommand.Execute(null);
        for (var i = 1; i <= 300; i++)
        {
            projects.NewItemTitle = $"Item {i}";
            projects.AddItemCommand.Execute(null);
        }

        var page = new ProjectsPage(projects);
        using var window = TabOrder.Host(page, 1100, 600);
        var todo = Cards(page).Single(list => ((BoardColumnViewModel)list.DataContext).Column == ProjectRules.Todo);
        var viewer = Find<ScrollViewer>(todo);

        Assert.Equal(300, todo.Items.Count);
        Assert.True(Count<ListBoxItem>(todo) < 60, "Only the cards in view, and a few around them, are drawn.");
        Assert.True(viewer.ScrollableHeight > 0, "The column scrolls on its own.");
        Assert.True(todo.ActualHeight <= 600, "The column fits the window instead of growing to the height of its cards.");
        viewer.ScrollToEnd();
        viewer.UpdateLayout();
        Assert.True(viewer.VerticalOffset > 0);
    });

    internal static ProjectsViewModel Page(TestPlanner planner, Action<string>? openTask = null) => new(
        planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, new ResourceStrings(Application.Current), _ => null, openTask ?? (_ => { }), action => action(), planner.Time, _ => { });

    /// <summary>The open columns' card lists, left to right.</summary>
    internal static List<ListBox> Cards(DependencyObject root)
    {
        var found = new List<ListBox>();
        var queue = new Queue<DependencyObject>([root]);
        while (queue.Count > 0)
        {
            var node = queue.Dequeue();
            if (node is ListBox { Name: "Cards", IsVisible: true } list)
            {
                found.Add(list);
            }

            for (var i = 0; i < VisualTreeHelper.GetChildrenCount(node); i++)
            {
                queue.Enqueue(VisualTreeHelper.GetChild(node, i));
            }
        }

        return found;
    }

    internal static T Find<T>(DependencyObject root)
        where T : DependencyObject
    {
        var queue = new Queue<DependencyObject>([root]);
        while (queue.Count > 0)
        {
            var node = queue.Dequeue();
            if (node is T found && node != root)
            {
                return found;
            }

            for (var i = 0; i < VisualTreeHelper.GetChildrenCount(node); i++)
            {
                queue.Enqueue(VisualTreeHelper.GetChild(node, i));
            }
        }

        throw new InvalidOperationException($"No {typeof(T).Name} under {root}.");
    }

    private static int Count<T>(DependencyObject node)
    {
        var count = node is T ? 1 : 0;
        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(node); i++)
        {
            count += Count<T>(VisualTreeHelper.GetChild(node, i));
        }

        return count;
    }
}

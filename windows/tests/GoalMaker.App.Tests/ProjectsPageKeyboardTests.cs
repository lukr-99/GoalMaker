using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// Opening items from the board (GM-122): one click on a card opens it, a click on its dots does not,
/// Enter opens the picked card, Left and Right go to the next column, and when the page shows again
/// the card that was opened is picked again, so the place in the list is kept.
/// </summary>
[Collection(nameof(WpfCollection))]
public sealed class ProjectsPageKeyboardTests
{
    [Fact]
    public void OneClickOnACardOpensTheItemButAClickOnItsDotsDoesNot() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        var opened = new List<string>();
        var (page, window) = Board(planner, opened);
        using (window)
        {
            var item = Item(page, "Ship the board");

            ProjectsPageScrollTests.Find<TextBlock>(item).RaiseEvent(Click());
            Assert.Equal([planner.Task("Ship the board").Id], opened);

            ProjectsPageScrollTests.Find<Button>(item).RaiseEvent(Click());
            Assert.Single(opened);
        }
    });

    [Fact]
    public void EnterOpensThePickedCardAndTheCardIsPickedAgainWhenThePageShowsAgain() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        var opened = new List<string>();
        var (page, window) = Board(planner, opened);
        using (window)
        {
            var list = ProjectsPageScrollTests.Cards(page).Single(cards => cards.Items.Count == 2);
            var card = list.Items.OfType<BoardItemViewModel>().Single(candidate => candidate.Title == "Write the docs");
            list.SelectedItem = card;

            Press(Item(page, "Write the docs"), Key.Enter);
            Assert.Equal([card.Id], opened);

            // Away to the item's page and back: the page is kept and shows again.
            list.SelectedItem = null;
            window.RootVisual = null;
            TabOrder.Settle();
            window.RootVisual = page;
            page.UpdateLayout();
            TabOrder.Settle();

            Assert.Same(card, ProjectsPageScrollTests.Cards(page).Single(cards => cards.Items.Count == 2).SelectedItem);
        }
    });

    [Fact]
    public void LeftAndRightGoToTheNextColumnWithCards() => WpfApp.Run(() =>
    {
        using var planner = new TestPlanner();
        var (page, window) = Board(planner, []);
        using (window)
        {
            var lists = ProjectsPageScrollTests.Cards(page);
            var backlog = lists.Single(list => ((BoardColumnViewModel)list.DataContext).Column == ProjectRules.Backlog);
            var todo = lists.Single(list => ((BoardColumnViewModel)list.DataContext).Column == ProjectRules.Todo);
            backlog.SelectedIndex = 0;

            Press(Item(page, "Widgets for habits"), Key.Right);

            Assert.NotNull(todo.SelectedItem);
        }
    });

    // A board with an idea in Backlog and two tasks in To do, drawn in a window offscreen.
    private static (ProjectsPage Page, System.Windows.Interop.HwndSource Window) Board(TestPlanner planner, List<string> opened)
    {
        var projects = ProjectsPageScrollTests.Page(planner, opened.Add);
        projects.NewCommand.Execute(null);
        projects.ProjectName = "GoalMaker";
        projects.SaveCommand.Execute(null);
        foreach (var (title, type) in new[] { ("Widgets for habits", ProjectRules.Idea), ("Ship the board", ProjectRules.Task), ("Write the docs", ProjectRules.Task) })
        {
            projects.NewItemType = type;
            projects.NewItemTitle = title;
            projects.AddItemCommand.Execute(null);
        }

        var page = new ProjectsPage(projects);
        return (page, TabOrder.Host(page, 1100, 700));
    }

    private static ListBoxItem Item(ProjectsPage page, string title)
    {
        foreach (var list in ProjectsPageScrollTests.Cards(page))
        {
            if (list.Items.OfType<BoardItemViewModel>().FirstOrDefault(card => card.Title == title) is { } card)
            {
                return (ListBoxItem)list.ItemContainerGenerator.ContainerFromItem(card);
            }
        }

        throw new InvalidOperationException($"No card {title}.");
    }

    // A click lets go of the left button: MouseUp bubbles from the element under the mouse, and each
    // element on the way raises its own MouseLeftButtonUp, as WPF does for a real click.
    private static MouseButtonEventArgs Click() => new(Mouse.PrimaryDevice, Environment.TickCount, MouseButton.Left)
    {
        RoutedEvent = UIElement.MouseUpEvent,
    };

    private static void Press(UIElement on, Key key) =>
        on.RaiseEvent(new KeyEventArgs(Keyboard.PrimaryDevice, PresentationSource.FromVisual(on)!, Environment.TickCount, key)
        {
            RoutedEvent = UIElement.PreviewKeyDownEvent,
        });
}

using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Projects page. Behavior is in <see cref="ProjectsViewModel"/>; the page only shows it, and
/// opens a card's menu when the dots are clicked.
/// </summary>
public partial class ProjectsPage
{
    public ProjectsPage(ProjectsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
    }

    // WPF opens a context menu on a right click only, so the dots button opens its own on a click,
    // dropped under the button rather than at the mouse.
    private void OnItemMenu(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement dots || dots.ContextMenu is not { } menu)
        {
            return;
        }

        menu.PlacementTarget = dots;
        menu.Placement = PlacementMode.Bottom;
        menu.IsOpen = true;
    }

    // The board scrolls sideways only, and a scroll viewer takes the wheel even when it cannot use it,
    // so the wheel over the board would do nothing. Hand it on to the page, which scrolls up and down.
    private void OnBoardWheel(object sender, MouseWheelEventArgs e)
    {
        if (e.Handled || sender is not FrameworkElement { Parent: UIElement page })
        {
            return;
        }

        e.Handled = true;
        page.RaiseEvent(new MouseWheelEventArgs(e.MouseDevice, e.Timestamp, e.Delta)
        {
            RoutedEvent = UIElement.MouseWheelEvent,
            Source = sender,
        });
    }
}

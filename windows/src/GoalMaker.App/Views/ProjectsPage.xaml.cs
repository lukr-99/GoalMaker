using System.Windows;
using System.Windows.Controls.Primitives;
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
}

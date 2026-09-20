using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Projects page. Behavior is in <see cref="ProjectsViewModel"/>; the page only shows it.</summary>
public partial class ProjectsPage
{
    public ProjectsPage(ProjectsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
    }
}

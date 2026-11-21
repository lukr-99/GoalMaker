using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Every open task, grouped by area. All behavior is in <see cref="AllTasksViewModel"/>.</summary>
public partial class AllTasksPage
{
    public AllTasksPage(AllTasksViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

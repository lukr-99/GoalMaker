using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Recent changes with undo. All behavior is in <see cref="ActivityViewModel"/>; the list is read again each time the page opens.</summary>
public partial class ActivityPage
{
    public ActivityPage(ActivityViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += async (_, _) => await viewModel.RefreshAsync();
    }
}

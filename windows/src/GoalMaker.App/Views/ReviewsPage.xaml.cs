using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Reviews page. Behavior is in <see cref="ReviewsViewModel"/>; the page only shows it.</summary>
public partial class ReviewsPage
{
    public ReviewsPage(ReviewsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
    }
}

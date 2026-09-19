using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The guided review. Behavior is in <see cref="ReviewViewModel"/>; the page keeps what was written
/// when it goes away.
/// </summary>
public partial class ReviewPage
{
    public ReviewPage(ReviewViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
        Unloaded += (_, _) => viewModel.SaveAnswers();
    }
}

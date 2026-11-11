using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Focus Board: a manually-ordered watchlist. All behavior is in <see cref="FocusViewModel"/>.</summary>
public partial class FocusPage
{
    public FocusPage(FocusViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

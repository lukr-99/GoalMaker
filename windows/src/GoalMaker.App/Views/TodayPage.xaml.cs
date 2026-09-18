using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Today (M1): the synced task list and the composer. All behavior is in <see cref="TodayViewModel"/>.</summary>
public partial class TodayPage
{
    public TodayPage(TodayViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

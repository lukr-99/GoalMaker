using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Stats page. Behavior is in <see cref="StatsViewModel"/>; the page only shows it.</summary>
public partial class StatsPage
{
    public StatsPage(StatsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
    }
}

using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Plan tomorrow (docs/plan-tomorrow.md). All behavior is in <see cref="PlanViewModel"/>.</summary>
public partial class PlanPage
{
    public PlanPage(PlanViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

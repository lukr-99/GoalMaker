using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The areas and tags manager. All behavior is in <see cref="AreasViewModel"/>.</summary>
public partial class AreasPage
{
    public AreasPage(AreasViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

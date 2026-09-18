using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Settings. All behavior is in <see cref="SettingsViewModel"/>.</summary>
public partial class SettingsPage
{
    public SettingsPage(SettingsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

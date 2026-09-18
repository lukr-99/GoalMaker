using System.Windows.Controls;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Shell;

/// <summary>The tray's Today flyout (Shell/TrayFlyout.xaml) around its view model.</summary>
public partial class TrayFlyout : UserControl
{
    public TrayFlyout(TrayFlyoutViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

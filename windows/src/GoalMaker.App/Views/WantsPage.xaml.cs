using System.Diagnostics;
using System.Windows.Navigation;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Wants page. Behavior is in <see cref="WantsViewModel"/>; the page only shows it.</summary>
public partial class WantsPage
{
    public WantsPage(WantsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
        // The add panel takes the keyboard when it opens, so a title can be typed straight away.
        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(WantsViewModel.IsEditing) && viewModel.IsEditing)
            {
                Dispatcher.BeginInvoke(() => DraftTitle.Focus());
            }
        };
    }

    // A want's link opens in the browser, never inside the app.
    private void OnLink(object sender, RequestNavigateEventArgs e)
    {
        if (e.Uri.Scheme is "http" or "https")
        {
            Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri) { UseShellExecute = true });
        }

        e.Handled = true;
    }
}

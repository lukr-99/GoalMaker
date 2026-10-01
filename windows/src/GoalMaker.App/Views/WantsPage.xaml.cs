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
        // The add panel sits at the top and takes the keyboard when it opens, so a title can be typed
        // straight away; from the bottom bar the title is there already and the reason is what is missing.
        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(WantsViewModel.IsEditing) && viewModel.IsEditing)
            {
                Scroller.ScrollToTop();
                Dispatcher.BeginInvoke(() => (viewModel.DraftTitle.Length > 0 && viewModel.DraftReason.Length == 0 ? DraftReason : DraftTitle).Focus());
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

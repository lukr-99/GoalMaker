using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Tally page. Behavior is in <see cref="TallyViewModel"/>; the page only shows it.</summary>
public partial class TallyPage
{
    public TallyPage(TallyViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
        // A panel takes the keyboard when it opens, so a pattern or a name can be typed straight away.
        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(TallyViewModel.IsEditingRule) && viewModel.IsEditingRule)
            {
                Dispatcher.BeginInvoke(() => DraftPattern.Focus());
            }
            else if (e.PropertyName == nameof(TallyViewModel.IsEditingCategory) && viewModel.IsEditingCategory)
            {
                Dispatcher.BeginInvoke(() => DraftName.Focus());
            }
        };
    }
}

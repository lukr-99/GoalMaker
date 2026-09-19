using System.ComponentModel;
using System.Windows.Media;
using System.Windows.Threading;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Goals page. Behavior is in <see cref="GoalsViewModel"/>; the page only moves the keyboard into
/// the editor and the log panel when they open, and throws the confetti.
/// </summary>
public partial class GoalsPage
{
    public GoalsPage(GoalsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
        viewModel.Celebrate += (_, _) => Confetti.Burst([.. new[] { "GM.AccentBrush", "GM.PrimaryBrush", "GM.HeroAccentBrush", "GM.HeroBrush" }
            .Select(key => TryFindResource(key)).OfType<Brush>()]);
        viewModel.Editor.PropertyChanged += (_, change) => FocusWhenOpened(change, nameof(GoalEditorViewModel.IsOpen), viewModel.Editor.IsOpen, TitleBox);
        viewModel.PropertyChanged += (_, change) => FocusWhenOpened(change, nameof(GoalsViewModel.IsLogging), viewModel.IsLogging, LogBox);
    }

    private void FocusWhenOpened(PropertyChangedEventArgs change, string property, bool open, System.Windows.Controls.Control box)
    {
        if (change.PropertyName == property && open)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Input, () => box.Focus());
        }
    }
}

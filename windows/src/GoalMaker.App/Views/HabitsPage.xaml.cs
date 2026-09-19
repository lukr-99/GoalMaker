using System.ComponentModel;
using System.Windows.Media;
using System.Windows.Threading;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Habits page. Behavior is in <see cref="HabitsViewModel"/>; the page only moves the keyboard into
/// the editor and the log panel when they open, and throws the confetti for a streak milestone.
/// </summary>
public partial class HabitsPage
{
    public HabitsPage(HabitsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
        viewModel.Celebrate += (_, _) => Confetti.Burst([.. new[] { "GM.AccentBrush", "GM.PrimaryBrush", "GM.HeroAccentBrush", "GM.HeroBrush" }
            .Select(key => TryFindResource(key)).OfType<Brush>()]);
        viewModel.Editor.PropertyChanged += (_, change) => FocusWhenOpened(change, nameof(HabitEditorViewModel.IsOpen), viewModel.Editor.IsOpen, NameBox);
        viewModel.PropertyChanged += (_, change) => FocusWhenOpened(change, nameof(HabitsViewModel.IsLogging), viewModel.IsLogging, LogBox);
    }

    private void FocusWhenOpened(PropertyChangedEventArgs change, string property, bool open, System.Windows.Controls.Control box)
    {
        if (change.PropertyName == property && open)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Input, () => box.Focus());
        }
    }
}

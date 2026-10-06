using System.ComponentModel;
using System.Windows;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// One task's details. Behavior is in <see cref="TaskDetailViewModel"/>; this swaps the notes between
/// reading and editing, and saves them when the box loses focus or the page closes.
/// </summary>
public partial class TaskPage
{
    private readonly TaskDetailViewModel viewModel;

    public TaskPage(TaskDetailViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        viewModel.PropertyChanged += OnChanged;
        Unloaded += (_, _) =>
        {
            viewModel.SaveNotes();
            viewModel.PropertyChanged -= OnChanged;
        };
        Show();
    }

    private void OnChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(TaskDetailViewModel.IsEditingNotes) or nameof(TaskDetailViewModel.HasTask))
        {
            Show();
        }
    }

    private void OnNotesLostFocus(object sender, RoutedEventArgs e) => viewModel.FinishNotes();

    // BooleanToVisibilityConverter can't invert, so the two "either this or that" parts are set here.
    private void Show()
    {
        NotesView.Visibility = viewModel.IsEditingNotes ? Visibility.Collapsed : Visibility.Visible;
        if (viewModel.IsEditingNotes && IsLoaded)
        {
            // Editing starts in the box, so leaving it later is what saves and closes it.
            Dispatcher.BeginInvoke(() => NotesBox.Focus(), System.Windows.Threading.DispatcherPriority.Input);
        }

        GoneText.Visibility = viewModel.HasTask ? Visibility.Collapsed : Visibility.Visible;
    }
}

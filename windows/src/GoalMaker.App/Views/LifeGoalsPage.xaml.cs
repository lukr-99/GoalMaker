using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Life goals page. Behavior is in <see cref="LifeGoalsViewModel"/>; the page only moves the
/// keyboard into the editor and the delete question when they open, hands dropped files to the editor,
/// and brings the card the why reminder asked for into view.
/// </summary>
public partial class LifeGoalsPage
{
    private readonly LifeGoalsViewModel viewModel;

    public LifeGoalsPage(LifeGoalsViewModel viewModel)
    {
        this.viewModel = viewModel;
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) =>
        {
            viewModel.Refresh();
            Dispatcher.BeginInvoke(DispatcherPriority.Background, MoveToRequested);
        };
        viewModel.Editor.PropertyChanged += (_, change) =>
        {
            if (change.PropertyName == nameof(LifeGoalEditorViewModel.IsOpen) && viewModel.Editor.IsOpen)
            {
                Dispatcher.BeginInvoke(DispatcherPriority.Input, () => TitleBox.Focus());
            }
        };
        // Cancel takes the keyboard, so Enter never deletes by accident.
        viewModel.PropertyChanged += (_, change) =>
        {
            if (change.PropertyName == nameof(LifeGoalsViewModel.IsConfirmingDelete) && viewModel.IsConfirmingDelete)
            {
                Dispatcher.BeginInvoke(DispatcherPriority.Input, () => CancelDelete.Focus());
            }
        };
        // A page not on screen moves when it is loaded again.
        viewModel.FocusRequested += (_, _) =>
        {
            if (IsLoaded)
            {
                Dispatcher.BeginInvoke(DispatcherPriority.Background, MoveToRequested);
            }
        };
    }

    // The card the why reminder's toast named: scrolled into view, with the keyboard on it.
    private void MoveToRequested()
    {
        if (viewModel.TakeFocusRequest() is not { } card)
        {
            return;
        }

        var cards = viewModel.Open.Contains(card) ? OpenCards : ClosedCards;
        cards.UpdateLayout();
        if (cards.ItemContainerGenerator.ContainerFromItem(card) is not FrameworkElement container)
        {
            return;
        }

        var target = VisualTreeHelper.GetChildrenCount(container) > 0 && VisualTreeHelper.GetChild(container, 0) is Button button
            ? (FrameworkElement)button
            : container;
        target.BringIntoView();
        target.Focus();
    }

    private void OnPictureDragOver(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop) ? DragDropEffects.Copy : DragDropEffects.None;
        e.Handled = true;
    }

    private async void OnPictureDrop(object sender, DragEventArgs e)
    {
        e.Handled = true;
        if (e.Data.GetData(DataFormats.FileDrop) is string[] paths && paths.Length > 0)
        {
            await viewModel.Editor.AddFilesAsync(paths).ConfigureAwait(true);
        }
    }
}

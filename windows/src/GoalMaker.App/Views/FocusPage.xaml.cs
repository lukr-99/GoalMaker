using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;
using GoalMaker.App.Controls;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Focus Board. Behavior is in <see cref="FocusViewModel"/>; this only moves the keyboard: Ctrl+N to the
/// quick add field, Ctrl+F to the search box, Alt+Up and Alt+Down on a row, Esc back to the board from the
/// add panel, and back onto a row after the board or the add panel is redrawn (a move, a completion, an
/// add), since the redraw drops the element that had the keyboard.
/// </summary>
public partial class FocusPage
{
    private readonly FocusViewModel viewModel;
    private readonly KeyboardSpot boardSpot = new();
    private readonly KeyboardSpot candidateSpot = new();
    private WeakReference<UIElement>? boardElement;
    private WeakReference<UIElement>? candidateElement;

    public FocusPage(FocusViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        PreviewKeyDown += OnPreviewKeyDown;
        KeyDown += OnKeyDown;
        BoardRows.PreviewGotKeyboardFocus += OnBoardGotKeyboardFocus;
        BoardRows.PreviewLostKeyboardFocus += (_, e) => ForgetWhenLeaving(BoardRows, boardSpot, e);
        Candidates.PreviewGotKeyboardFocus += OnCandidateGotKeyboardFocus;
        Candidates.PreviewLostKeyboardFocus += (_, e) => ForgetWhenLeaving(Candidates, candidateSpot, e);
        viewModel.PropertyChanged += OnViewModelChanged;
        Unloaded += (_, _) => viewModel.PropertyChanged -= OnViewModelChanged;
        Loaded += (_, _) =>
        {
            viewModel.PropertyChanged -= OnViewModelChanged;
            viewModel.PropertyChanged += OnViewModelChanged;
        };
    }

    private bool IsWide => Equals(Root.Tag, "wide");

    private void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        var modifiers = Keyboard.Modifiers;
        if (modifiers == ModifierKeys.Control && key == Key.N)
        {
            if (!IsWide && viewModel.IsAddOpen) viewModel.CloseAddDialogCommand.Execute(null);
            Dispatcher.BeginInvoke(DispatcherPriority.Input, () => QuickAddBox.Focus());
            e.Handled = true;
        }
        else if (modifiers == ModifierKeys.Control && key == Key.F)
        {
            if (IsWide) SearchBox.Focus();
            else if (viewModel.IsAddOpen) SearchBox.Focus();
            else viewModel.ShowAddDialogCommand.Execute(null);
            e.Handled = true;
        }
        else if (modifiers == ModifierKeys.Alt && key is Key.Up or Key.Down
                 && (Keyboard.FocusedElement as FrameworkElement)?.DataContext is FocusRowViewModel row)
        {
            (key == Key.Up ? viewModel.MoveUpCommand : viewModel.MoveDownCommand).Execute(row);
            e.Handled = true;
        }
    }

    // Esc anywhere on the page while the add panel is open goes back to the board. The search box's own Esc
    // (clear what is typed first) and an open drop-down handle theirs before this sees it.
    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Escape || Keyboard.Modifiers != ModifierKeys.None || !viewModel.IsAddOpen) return;
        viewModel.CloseAddDialogCommand.Execute(null);
        e.Handled = true;
    }

    private void OnBoardGotKeyboardFocus(object sender, KeyboardFocusChangedEventArgs e)
    {
        if (e.NewFocus is not FrameworkElement element || element.DataContext is not FocusRowViewModel row) return;
        boardSpot.Remember(row.Id, viewModel.Items.IndexOf(row), element.Name is "Up" or "Down" or "Off" ? element.Name : "Done");
        boardElement = new WeakReference<UIElement>(element);
    }

    private void OnCandidateGotKeyboardFocus(object sender, KeyboardFocusChangedEventArgs e)
    {
        if (e.NewFocus is not FrameworkElement element || element.DataContext is not FocusAddRowViewModel row) return;
        candidateSpot.Remember(row.Id, viewModel.Candidates.IndexOf(row), "Add");
        candidateElement = new WeakReference<UIElement>(element);
    }

    // The keyboard went to something else on show (not nowhere, as when its row is redrawn): forget the row,
    // so a later redraw, such as a sync, does not pull the keyboard back onto it.
    private void ForgetWhenLeaving(ItemsControl list, KeyboardSpot spot, KeyboardFocusChangedEventArgs e)
    {
        if (e.NewFocus is UIElement next and not Window && next != this && next.IsVisible && !list.IsAncestorOf(next))
        {
            spot.Forget();
        }
    }

    private void OnViewModelChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(FocusViewModel.Items) && boardSpot.IsSet)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Loaded, RestoreBoard);
        }
        else if (e.PropertyName == nameof(FocusViewModel.Candidates) && candidateSpot.IsSet)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Loaded, RestoreCandidate);
        }
    }

    // Whether the keyboard was dropped by a redraw: the element that had it is gone, and it has not moved on
    // to anything else on show.
    private bool WasDropped(WeakReference<UIElement>? had)
    {
        if (had is null || !had.TryGetTarget(out var before) || before.IsVisible) return false;
        var now = Keyboard.FocusedElement as UIElement;
        return now is null or Window || !now.IsVisible || now == this;
    }

    // Puts the keyboard on the same task (moved), or on the row now in its place (completed or taken off).
    private void RestoreBoard()
    {
        if (!WasDropped(boardElement)) return;
        var items = viewModel.Items;
        if (boardSpot.Pick([.. items.Select(item => item.Id)]) is not { } index)
        {
            QuickAddBox.Focus();
            return;
        }

        if (BoardRows.ItemContainerGenerator.ContainerFromItem(items[index]) is not ContentPresenter presenter) return;
        presenter.ApplyTemplate();
        var template = presenter.ContentTemplate;
        var target = template?.FindName(boardSpot.Part, presenter) as UIElement;
        if (target is null || !target.IsEnabled || target.Visibility != Visibility.Visible) target = template?.FindName("Done", presenter) as UIElement;
        target?.Focus();
    }

    // After an add from the panel: the next task offered in its place, or the search box when none is left.
    private void RestoreCandidate()
    {
        if (!WasDropped(candidateElement)) return;
        var rows = viewModel.Candidates;
        if (candidateSpot.Pick([.. rows.Select(row => row.Id)]) is { } index
            && Candidates.ItemContainerGenerator.ContainerFromItem(rows[index]) is ContentPresenter presenter
            && System.Windows.Media.VisualTreeHelper.GetChildrenCount(presenter) > 0
            && System.Windows.Media.VisualTreeHelper.GetChild(presenter, 0) is UIElement button
            && button.IsVisible)
        {
            button.Focus();
            return;
        }

        candidateSpot.Forget();
        if (SearchBox.IsVisible) SearchBox.Focus();
    }
}

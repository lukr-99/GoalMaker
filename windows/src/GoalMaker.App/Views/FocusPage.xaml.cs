using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Focus Board. Behavior is in <see cref="FocusViewModel"/>; this only moves the keyboard: Ctrl+N to the
/// quick add field, Ctrl+F to the search box, Alt+Up and Alt+Down on a row, and back onto a row after the
/// board is redrawn (a move, a completion), since the redraw drops the element that had the keyboard.
/// </summary>
public partial class FocusPage
{
    private readonly FocusViewModel viewModel;

    // Where the keyboard was on the board: the row's task, its place and which part of it.
    private string? keyboardRowId;
    private int keyboardRowIndex;
    private string keyboardPart = "Done";
    private WeakReference<UIElement>? keyboardElement;

    public FocusPage(FocusViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        PreviewKeyDown += OnPreviewKeyDown;
        BoardRows.PreviewGotKeyboardFocus += OnBoardGotKeyboardFocus;
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

    private void OnBoardGotKeyboardFocus(object sender, KeyboardFocusChangedEventArgs e)
    {
        if (e.NewFocus is not FrameworkElement element || element.DataContext is not FocusRowViewModel row) return;
        keyboardRowId = row.Id;
        keyboardRowIndex = viewModel.Items.IndexOf(row);
        keyboardPart = element.Name is "Up" or "Down" or "Off" ? element.Name : "Done";
        keyboardElement = new WeakReference<UIElement>(element);
    }

    private void OnViewModelChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName != nameof(FocusViewModel.Items) || keyboardRowId is null) return;
        Dispatcher.BeginInvoke(DispatcherPriority.Loaded, RestoreKeyboard);
    }

    // Once the board is redrawn: if the keyboard was on a row that is gone and has not moved on to anything
    // else on show, put it on the same task (moved), or on the row now in its place (completed or taken off).
    private void RestoreKeyboard()
    {
        if (keyboardElement is null || !keyboardElement.TryGetTarget(out var before) || before.IsVisible) return;
        var now = Keyboard.FocusedElement as UIElement;
        if (now is not null and not Window && now.IsVisible && now != this) return;
        var items = viewModel.Items;
        if (items.Count == 0)
        {
            QuickAddBox.Focus();
            return;
        }

        var row = items.FirstOrDefault(item => item.Id == keyboardRowId) ?? items[Math.Clamp(keyboardRowIndex, 0, items.Count - 1)];
        if (BoardRows.ItemContainerGenerator.ContainerFromItem(row) is not ContentPresenter presenter) return;
        presenter.ApplyTemplate();
        var template = presenter.ContentTemplate;
        var target = template?.FindName(keyboardPart, presenter) as UIElement;
        if (target is null || !target.IsEnabled || target.Visibility != Visibility.Visible) target = template?.FindName("Done", presenter) as UIElement;
        target?.Focus();
    }
}

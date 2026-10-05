using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Calendar page. Behavior is in <see cref="CalendarViewModel"/>; the page only shows it, and
/// carries the drag of a task from a day's list onto another day of the grid (docs/calendar.md), the
/// picking of several days with Ctrl and Shift (a click, or Space on a cell), and moves the keyboard
/// into the event editor when it opens.
/// </summary>
public partial class CalendarPage
{
    // How far the mouse travels with the button down before it counts as a drag, not a click.
    private static readonly double DragStart = SystemParameters.MinimumHorizontalDragDistance;
    private Point pressedAt;
    private string? pressedId;

    public CalendarPage(CalendarViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) =>
        {
            viewModel.Refresh();

            // Opened from Today's line before the page was on screen.
            if (viewModel.Editor is { IsOpen: true })
            {
                Dispatcher.BeginInvoke(DispatcherPriority.Input, () => EventTitleBox.Focus());
            }
        };
        if (viewModel.Editor is { } editor)
        {
            editor.PropertyChanged += (_, change) =>
            {
                if (change.PropertyName == nameof(EventEditorViewModel.IsOpen) && editor.IsOpen)
                {
                    Dispatcher.BeginInvoke(DispatcherPriority.Input, () => EventTitleBox.Focus());
                }
            };
        }
    }

    private void OnEntryPressed(object sender, MouseButtonEventArgs e)
    {
        pressedAt = e.GetPosition(this);
        pressedId = (sender as FrameworkElement)?.DataContext is CalendarEntryViewModel entry ? entry.Id : null;
    }

    private void OnEntryMove(object sender, MouseEventArgs e)
    {
        if (pressedId is not { } id || e.LeftButton != MouseButtonState.Pressed)
        {
            return;
        }

        var moved = e.GetPosition(this) - pressedAt;
        if (Math.Abs(moved.X) < DragStart && Math.Abs(moved.Y) < DragStart)
        {
            return;
        }

        pressedId = null;
        DragDrop.DoDragDrop((DependencyObject)sender, new DataObject(typeof(string), id), DragDropEffects.Move);
    }

    // Ctrl+click adds a day to the pick or takes it out, Shift+click picks a run; a plain click opens the day.
    private void OnCellPressed(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: CalendarCellViewModel cell } && PickWith(cell, Keyboard.Modifiers))
        {
            e.Handled = true;
        }
    }

    // Ctrl+Space and Shift+Space do the same on the cell the keyboard is on, and the keyboard stays there.
    private void OnCellKey(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Space && sender is FrameworkElement { DataContext: CalendarCellViewModel cell } && PickWith(cell, Keyboard.Modifiers))
        {
            e.Handled = true;
            var day = cell.Day;
            Dispatcher.BeginInvoke(DispatcherPriority.Input, () => FocusCell(this, day));
        }
    }

    private static bool PickWith(CalendarCellViewModel cell, ModifierKeys modifiers)
    {
        if (modifiers == ModifierKeys.Control)
        {
            cell.PickCommand.Execute(null);
            return true;
        }

        if (modifiers == ModifierKeys.Shift)
        {
            cell.PickRunCommand.Execute(null);
            return true;
        }

        return false;
    }

    // The grid is built again after a pick, so the keyboard goes back to the new cell of the same day.
    private static bool FocusCell(DependencyObject parent, DateOnly day)
    {
        for (var index = 0; index < VisualTreeHelper.GetChildrenCount(parent); index++)
        {
            var child = VisualTreeHelper.GetChild(parent, index);
            if (child is ButtonBase { DataContext: CalendarCellViewModel cell } button && cell.Day == day)
            {
                return button.Focus();
            }

            if (FocusCell(child, day))
            {
                return true;
            }
        }

        return false;
    }

    private void OnCellDragOver(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(typeof(string)) ? DragDropEffects.Move : DragDropEffects.None;
        e.Handled = true;
    }

    private void OnCellDrop(object sender, DragEventArgs e)
    {
        if (DataContext is not CalendarViewModel page
            || (sender as FrameworkElement)?.DataContext is not CalendarCellViewModel cell
            || e.Data.GetData(typeof(string)) is not string id)
        {
            return;
        }

        page.MoveTo(id, cell.Day);
        e.Handled = true;
    }
}

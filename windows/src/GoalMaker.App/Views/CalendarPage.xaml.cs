using System.Windows;
using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Calendar page. Behavior is in <see cref="CalendarViewModel"/>; the page only shows it, and
/// carries the drag of a task from a day's list onto another day of the grid (docs/calendar.md).
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
        Loaded += (_, _) => viewModel.Refresh();
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

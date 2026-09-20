using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One day of the calendar grid (docs/calendar.md).</summary>
public sealed class CalendarCellViewModel(
    DateOnly day,
    string label,
    int count,
    bool isToday,
    bool inPeriod,
    bool isSelected,
    Action open)
{
    public DateOnly Day { get; } = day;

    /// <summary>The day of the month, which is all a cell has room for.</summary>
    public string Label { get; } = label;

    /// <summary>How much the day holds, which the bar under the number grows with.</summary>
    public int Count { get; } = count;

    /// <summary>The bar's width in pixels, up to four things.</summary>
    public double BarWidth { get; } = count == 0 ? 0 : 6 + (4 * Math.Min(count, 4));

    public bool HasAnything { get; } = count > 0;

    public bool IsToday { get; } = isToday;

    /// <summary>Whether the day belongs to the month on show; the ones around it are faint.</summary>
    public bool InPeriod { get; } = inPeriod;

    public bool IsSelected { get; } = isSelected;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);
}

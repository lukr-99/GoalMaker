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
    Action open,
    string? automationName = null,
    bool isPicked = false,
    Action? pick = null,
    Action? pickRun = null)
{
    public DateOnly Day { get; } = day;

    /// <summary>
    /// The cell as a screen reader says it (M6-05): the date, whether it is today or open, and what is
    /// on it, since the number and the bar alone say little.
    /// </summary>
    public string AutomationName { get; } = automationName ?? day.ToString("D", System.Globalization.CultureInfo.CurrentCulture);

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

    /// <summary>Whether the day is one of several picked, which outlines it.</summary>
    public bool IsPicked { get; } = isPicked;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);

    /// <summary>Ctrl+click or Ctrl+Space: adds the day to the pick, or takes it out.</summary>
    public IRelayCommand PickCommand { get; } = new RelayCommand(pick ?? open);

    /// <summary>Shift+click or Shift+Space: picks the run of days from the last one picked.</summary>
    public IRelayCommand PickRunCommand { get; } = new RelayCommand(pickRun ?? open);
}

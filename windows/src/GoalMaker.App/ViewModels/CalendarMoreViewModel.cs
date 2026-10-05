using System.Windows;

namespace GoalMaker.App.ViewModels;

/// <summary>"+2" under a day whose events need more than the three lanes a row draws (docs/calendar.md).</summary>
public sealed class CalendarMoreViewModel(int column, string text, string automationName)
{
    public int Column { get; } = column;

    public string Text { get; } = text;

    public string AutomationName { get; } = automationName;

    /// <summary>Under the three lanes.</summary>
    public Thickness Margin { get; } = new(4, CalendarWeekViewModel.Lanes * CalendarBarViewModel.LaneHeight, 0, 0);
}

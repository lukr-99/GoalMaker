using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>An event the open day lists above its tasks (docs/calendar.md): its title and days; it opens the editor.</summary>
public sealed class CalendarEventViewModel(string id, string title, string days, Brush? brush, string automationName, Action open)
{
    public string Id { get; } = id;

    public string Title { get; } = title;

    /// <summary>"12 to 15 October".</summary>
    public string Days { get; } = days;

    /// <summary>The area's colour, or null for the accent.</summary>
    public Brush? Brush { get; } = brush;

    public string AutomationName { get; } = automationName;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);
}

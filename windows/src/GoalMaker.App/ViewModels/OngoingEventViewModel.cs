using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>An event going on, on Today's slim line above the tasks: "Prague · day 2 of 4". It opens the event's editor.</summary>
public sealed class OngoingEventViewModel(string id, string text, Brush? brush, string automationName, Action open)
{
    public string Id { get; } = id;

    public string Text { get; } = text;

    /// <summary>The area's colour, or null for the accent.</summary>
    public Brush? Brush { get; } = brush;

    public string AutomationName { get; } = automationName;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);
}

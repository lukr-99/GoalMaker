using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One line of a task's reminder menu: a reminder to set, or one to take away.</summary>
public sealed record ReminderChoice(string Label, IRelayCommand Command);

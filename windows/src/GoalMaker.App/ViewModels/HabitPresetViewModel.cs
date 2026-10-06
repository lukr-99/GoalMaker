using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One ready tap of the log panel (docs/habits.md, "One tap"): a quarter or a half of the target, or the
/// rest of it, as "0.63 L" or "Rest: 1.5 L". One click logs <see cref="Amount"/>.
/// </summary>
public sealed record HabitPresetViewModel(double Amount, string Text, IRelayCommand Command);

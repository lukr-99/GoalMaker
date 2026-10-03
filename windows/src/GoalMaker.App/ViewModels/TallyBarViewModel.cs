using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One upright bar of a Tally chart, a day or a week (docs/tally.md): what it stands for, its time,
/// how tall it is against the biggest bar of its chart, its categories stacked from the bottom, and
/// the tooltip that names it with its time. A day of the Tally page's week is also a button
/// (<see cref="Pick"/>) that shows the day closer up, and <see cref="IsSelected"/> while it does.
/// </summary>
public sealed record TallyBarViewModel(
    string Label,
    string Value,
    double Fraction,
    IReadOnlyList<(double Amount, Brush? Brush)> Parts,
    string Tip,
    bool IsCurrent = false,
    bool IsSelected = false,
    IRelayCommand? Pick = null);

using System.Windows.Media;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One upright bar of a Tally chart, a day or a week (docs/tally.md): what it stands for, its time,
/// how tall it is against the biggest bar of its chart, its categories stacked from the bottom, and
/// the tooltip that names it with its time.
/// </summary>
public sealed record TallyBarViewModel(string Label, string Value, double Fraction, IReadOnlyList<(double Amount, Brush? Brush)> Parts, string Tip, bool IsCurrent = false);

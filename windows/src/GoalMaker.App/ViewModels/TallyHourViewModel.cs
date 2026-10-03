using System.Windows.Media;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One clock hour of the Tally page's day on this PC (docs/tally.md): its share of the hour, its
/// categories stacked from the bottom, and the tooltip that names it with its time.
/// </summary>
public sealed record TallyHourViewModel(double Fraction, IReadOnlyList<(double Amount, Brush? Brush)> Parts, string Tip);

namespace GoalMaker.Core.Planning;

/// <summary>
/// One day of a habit's heatmap (docs/habits.md): <see cref="Kind"/> says whether there is nothing,
/// a pause, a skip, or a <see cref="Fraction"/> of the day's target from 0 to 1.
/// </summary>
public readonly record struct HabitHeat(HabitHeatKind Kind, double Fraction = 0)
{
    public static HabitHeat None => new(HabitHeatKind.None);

    public static HabitHeat Paused => new(HabitHeatKind.Paused);

    public static HabitHeat Skipped => new(HabitHeatKind.Skipped);

    public static HabitHeat Share(double fraction) => new(HabitHeatKind.Share, fraction);
}

using System.Globalization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>The date a Focus row shows: the deadline, else the planned day, and whether it has passed.</summary>
internal static class FocusDate
{
    public static (string Text, bool IsLate) For(TaskItem task, DateOnly today) =>
        (task.Deadline ?? task.PlannedDate) is { } date
            ? (date.ToString("d MMM", CultureInfo.CurrentCulture), date < today)
            : (string.Empty, false);
}

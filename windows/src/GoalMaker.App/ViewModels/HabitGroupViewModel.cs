using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One group of the Habits page (docs/habits.md): Every day, Weekly or Limits, its header with how many
/// habits it holds, and the rows shown after Hide done. <see cref="IsAllDone"/> says so when Hide done
/// took every row.
/// </summary>
public sealed record HabitGroupViewModel(HabitGroup Group, string Header, IReadOnlyList<HabitRowViewModel> Rows, int Total)
{
    public bool IsAllDone => Rows.Count == 0 && Total > 0;
}

namespace GoalMaker.Core.Planning;

/// <summary>
/// The numbers behind the stats screen (docs/stats.md, contracts/vectors/stats.json): tasks finished a
/// week at a time, goals hit a month at a time, how each habit is holding up, and past ratings.
/// </summary>
public static class StatsRules
{
    /// <summary>How many weeks the tasks and habits cover by default.</summary>
    public const int Weeks = 12;

    /// <summary>How many months the goals cover by default.</summary>
    public const int Months = 6;

    /// <summary>How many past reviews the mood and energy chart holds.</summary>
    public const int Ratings = 12;

    public static StatsDigest Build(
        IReadOnlyList<TaskItem> tasks,
        IReadOnlyList<GoalItem> goals,
        IReadOnlyList<GoalEntryItem> entries,
        IReadOnlyList<HabitItem> habits,
        IReadOnlyList<HabitCheckin> checkins,
        IReadOnlyList<HabitPause> pauses,
        IReadOnlyList<ReviewItem> reviews,
        DateOnly today,
        int weekCount = Weeks,
        int monthCount = Months,
        string ratingKind = ReviewRules.Weekly,
        int ratingCount = Ratings) => new()
        {
            Weeks = WeeksDone(tasks, today, weekCount),
            Months = MonthsHit(goals, tasks, entries, habits, checkins, today, monthCount),
            Habits = HabitRows(habits, checkins, pauses, today, weekCount),
            Ratings = RatingRows(reviews, ratingKind, ratingCount),
        };

    /// <summary>Tasks finished in each of the last <paramref name="count"/> weeks, oldest first, the last one holding today.</summary>
    public static IReadOnlyList<StatsDigest.Week> WeeksDone(IReadOnlyList<TaskItem> tasks, DateOnly today, int count = Weeks)
    {
        var last = GoalRules.PeriodStart(GoalHorizon.Week, today);
        var wanted = Math.Max(count, 0);
        var first = last.AddDays(-7 * Math.Max(wanted - 1, 0));
        var byWeek = tasks
            .Where(task => !task.Deleted)
            .Select(task => task.CompletedDay)
            .Where(day => day is { } finished && finished >= first && finished <= today)
            .GroupBy(day => GoalRules.PeriodStart(GoalHorizon.Week, day!.Value))
            .ToDictionary(group => group.Key, group => group.Count());
        return [.. Enumerable.Range(0, wanted).Select(step =>
        {
            var start = first.AddDays(7 * step);
            return new StatsDigest.Week(start, byWeek.TryGetValue(start, out var done) ? done : 0);
        })];
    }

    /// <summary>The goals of each of the last <paramref name="count"/> months, oldest first, and how many were hit.</summary>
    public static IReadOnlyList<StatsDigest.Month> MonthsHit(
        IReadOnlyList<GoalItem> goals,
        IReadOnlyList<TaskItem> tasks,
        IReadOnlyList<GoalEntryItem> entries,
        IReadOnlyList<HabitItem> habits,
        IReadOnlyList<HabitCheckin> checkins,
        DateOnly today,
        int count = Months)
    {
        var wanted = Math.Max(count, 0);
        var first = new DateOnly(today.Year, today.Month, 1).AddMonths(-Math.Max(wanted - 1, 0));
        var live = goals.Where(goal => !goal.Deleted && goal.Status != GoalRules.Dropped && goal.Horizon == GoalHorizon.Month).ToList();
        var tasksByGoal = tasks.Where(task => !task.Deleted && task.GoalId is not null).ToLookup(task => task.GoalId!, StringComparer.Ordinal);
        var entriesByGoal = entries.ToLookup(entry => entry.GoalId, StringComparer.Ordinal);
        return [.. Enumerable.Range(0, wanted).Select(step =>
        {
            var start = first.AddMonths(step);
            var month = live.Where(goal => GoalRules.PeriodStart(GoalHorizon.Month, goal.PeriodStart) == start).ToList();
            var hit = month.Count(goal => GoalRules.ProgressOf(goal, tasksByGoal[goal.Id], entriesByGoal[goal.Id], habits, checkins).Hit);
            return new StatsDigest.Month(start, hit, month.Count);
        })];
    }

    /// <summary>How each habit that is not archived did over the last <paramref name="weeks"/> weeks, in the order they are kept.</summary>
    public static IReadOnlyList<StatsDigest.Habit> HabitRows(
        IReadOnlyList<HabitItem> habits,
        IReadOnlyList<HabitCheckin> checkins,
        IReadOnlyList<HabitPause> pauses,
        DateOnly today,
        int weeks = Weeks)
    {
        var from = GoalRules.PeriodStart(GoalHorizon.Week, today).AddDays(-7 * Math.Max(weeks - 1, 0));
        return [.. habits.Where(habit => !habit.Deleted && !habit.Archived).Select(habit =>
        {
            var own = checkins.Where(checkin => checkin.HabitId == habit.Id).ToList();
            var rests = pauses.Where(pause => pause.HabitId == habit.Id).ToList();
            var states = HabitRules.PeriodsBetween(habit, from, today).Select(start => HabitRules.State(habit, start, today, own, rests)).ToList();
            return new StatsDigest.Habit(
                habit.Id,
                habit.Name,
                habit.Emoji,
                states.Count(state => state == HabitPeriodState.Met),
                states.Count(state => state != HabitPeriodState.None),
                HabitRules.Streak(habit, today, own, rests),
                Best(states));
        })];
    }

    /// <summary>The ratings of the last <paramref name="count"/> reviews of <paramref name="kind"/> that rated anything, oldest first.</summary>
    public static IReadOnlyList<StatsDigest.Rating> RatingRows(IReadOnlyList<ReviewItem> reviews, string kind = ReviewRules.Weekly, int count = Ratings)
    {
        var rated = reviews
            .Where(review => !review.Deleted && review.Kind == kind && (review.Mood is not null || review.Energy is not null))
            .OrderBy(review => review.PeriodStart)
            .ToList();
        var wanted = Math.Max(count, 0);
        return [.. rated.Skip(Math.Max(rated.Count - wanted, 0)).Select(review => new StatsDigest.Rating(review.PeriodStart, review.Mood, review.Energy))];
    }

    // The longest run of met periods: a missed one ends a run, the rest are passed over like a streak.
    private static int Best(IEnumerable<HabitPeriodState> states)
    {
        var best = 0;
        var run = 0;
        foreach (var state in states)
        {
            if (state == HabitPeriodState.Met)
            {
                run++;
                best = Math.Max(best, run);
            }
            else if (state == HabitPeriodState.Missed)
            {
                run = 0;
            }
        }

        return best;
    }
}

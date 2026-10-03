namespace GoalMaker.Core.Planning;

/// <summary>
/// The numbers behind the stats screen (docs/stats.md, contracts/vectors/stats.json): tasks finished a
/// week at a time with the project work counted apart, goals hit a month at a time, how each habit is
/// holding up, and past ratings.
/// </summary>
public static class StatsRules
{
    /// <summary>How many weeks the tasks and habits cover by default.</summary>
    public const int Weeks = 12;

    /// <summary>How many months the goals cover by default.</summary>
    public const int Months = 6;

    /// <summary>How many past reviews the mood and energy chart holds.</summary>
    public const int Ratings = 12;

    /// <summary>How many projects the stats screen's By project block lists.</summary>
    public const int TopProjects = 5;

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
        int ratingCount = Ratings,
        IReadOnlyList<ProjectItem>? projects = null) => new()
        {
            Weeks = WeeksDone(tasks, today, weekCount, projects),
            Months = MonthsHit(goals, tasks, entries, habits, checkins, today, monthCount),
            Habits = HabitRows(habits, checkins, pauses, today, weekCount),
            Ratings = RatingRows(reviews, ratingKind, ratingCount),
            ByProject = ByProject(tasks, projects ?? [], today, weekCount),
        };

    /// <summary>
    /// Tasks finished in each of the last <paramref name="count"/> weeks, oldest first, the last one holding today,
    /// and how many of them were project work: items of one of <paramref name="projects"/> that is not deleted.
    /// </summary>
    public static IReadOnlyList<StatsDigest.Week> WeeksDone(
        IReadOnlyList<TaskItem> tasks,
        DateOnly today,
        int count = Weeks,
        IReadOnlyList<ProjectItem>? projects = null)
    {
        var wanted = Math.Max(count, 0);
        var first = FirstWeek(today, wanted);
        var live = (projects ?? []).Where(project => !project.Deleted).Select(project => project.Id).ToHashSet(StringComparer.Ordinal);
        var byWeek = Finished(tasks, first, today).ToLookup(
            row => GoalRules.PeriodStart(GoalHorizon.Week, row.Day),
            row => row.Task.ProjectId is { } id && live.Contains(id));
        return [.. Enumerable.Range(0, wanted).Select(step =>
        {
            var start = first.AddDays(7 * step);
            var week = byWeek[start].ToList();
            return new StatsDigest.Week(start, week.Count, week.Count(project => project));
        })];
    }

    /// <summary>
    /// The project work of the last <paramref name="count"/> weeks per project that is not deleted, most first; the
    /// projects with none are left out, and ties keep the order <paramref name="projects"/> has them in.
    /// </summary>
    public static IReadOnlyList<StatsDigest.ProjectDone> ByProject(
        IReadOnlyList<TaskItem> tasks,
        IReadOnlyList<ProjectItem> projects,
        DateOnly today,
        int count = Weeks)
    {
        var byId = Finished(tasks, FirstWeek(today, Math.Max(count, 0)), today)
            .Where(row => row.Task.ProjectId is not null)
            .GroupBy(row => row.Task.ProjectId!, StringComparer.Ordinal)
            .ToDictionary(group => group.Key, group => group.Count(), StringComparer.Ordinal);
        return [.. projects
            .Where(project => !project.Deleted)
            .DistinctBy(project => project.Id, StringComparer.Ordinal)
            .Where(project => byId.ContainsKey(project.Id))
            .Select(project => new StatsDigest.ProjectDone(project.Id, project.Name, byId[project.Id]))
            .OrderByDescending(row => row.Done)];
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
        var from = FirstWeek(today, weeks);
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

    // The Monday of the first of the last count weeks.
    private static DateOnly FirstWeek(DateOnly today, int count) =>
        GoalRules.PeriodStart(GoalHorizon.Week, today).AddDays(-7 * Math.Max(count - 1, 0));

    // The tasks finished from first to today, each with the day it was finished.
    private static IEnumerable<(DateOnly Day, TaskItem Task)> Finished(IReadOnlyList<TaskItem> tasks, DateOnly first, DateOnly today) =>
        tasks
            .Where(task => !task.Deleted && task.CompletedDay is { } day && day >= first && day <= today)
            .Select(task => (task.CompletedDay!.Value, task));

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

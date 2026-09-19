using System.Globalization;

namespace GoalMaker.Core.Planning;

/// <summary>
/// What a review of a period looks back on (docs/reviews.md): the tasks done day by day against the
/// period before, the goals and habits of the period, the tasks still open, and the facts the reactive
/// prompts read (contracts/content/prompts.json).
/// </summary>
public static class ReviewLookBack
{
    /// <summary>A weekly review looks back over the week, a monthly one over the month, a yearly one over the year.</summary>
    public static DateOnly PeriodEnd(string kind, DateOnly start) => kind switch
    {
        ReviewRules.Monthly => start.AddMonths(1).AddDays(-1),
        ReviewRules.Yearly => start.AddYears(1).AddDays(-1),
        _ => start.AddDays(6),
    };

    /// <summary>The period before the one starting on <paramref name="start"/>.</summary>
    public static DateOnly PreviousStart(string kind, DateOnly start) => kind switch
    {
        ReviewRules.Monthly => start.AddMonths(-1),
        ReviewRules.Yearly => start.AddYears(-1),
        _ => start.AddDays(-7),
    };

    /// <summary>The goal horizon a review of <paramref name="kind"/> looks at.</summary>
    public static GoalHorizon HorizonOf(string kind) => kind switch
    {
        ReviewRules.Monthly => GoalHorizon.Month,
        ReviewRules.Yearly => GoalHorizon.Year,
        _ => GoalHorizon.Week,
    };

    /// <summary>How much of the period has gone by, 0 to 1: what a goal should have reached by now.</summary>
    public static double Expected(DateOnly start, DateOnly end, DateOnly today)
    {
        double length = end.DayNumber - start.DayNumber + 1;
        if (length <= 0)
        {
            return 1;
        }

        double gone = (today < end ? today : end).DayNumber - start.DayNumber + 1;
        return Math.Clamp(gone / length, 0, 1);
    }

    public static ReviewDigest Build(
        string kind,
        DateOnly periodStart,
        IEnumerable<TaskItem> tasks,
        IEnumerable<AreaItem> areas,
        IEnumerable<GoalItem> goals,
        IEnumerable<GoalEntryItem> entries,
        IReadOnlyList<HabitItem> habits,
        IReadOnlyList<HabitCheckin> checkins,
        IReadOnlyList<HabitPause> pauses,
        DateOnly today)
    {
        var end = PeriodEnd(kind, periodStart);
        var live = tasks.Where(task => !task.Deleted).ToList();
        var completed = live.Select(task => (Day: CompletedOn(task), Task: task)).Where(entry => entry.Day is not null).ToList();
        var inPeriod = completed.Where(entry => entry.Day >= periodStart && entry.Day <= end).ToList();
        var previous = PreviousStart(kind, periodStart);
        var beforeEnd = PeriodEnd(kind, previous);
        var doneBefore = completed.Count(entry => entry.Day >= previous && entry.Day <= beforeEnd);

        var last = today < end ? today : end;
        var byDay = inPeriod.GroupBy(entry => entry.Day!.Value).ToDictionary(group => group.Key, group => group.Count());
        var days = new List<ReviewDigest.Day>();
        for (var day = periodStart; day <= last; day = day.AddDays(1))
        {
            days.Add(new ReviewDigest.Day(day, byDay.TryGetValue(day, out var count) ? count : 0));
        }

        var areaNames = areas.ToDictionary(area => area.Id, area => area.Name, StringComparer.Ordinal);
        var strongest = inPeriod
            .Where(entry => entry.Task.AreaId is not null)
            .GroupBy(entry => entry.Task.AreaId!)
            .OrderByDescending(group => group.Count())
            .ThenBy(group => group.Key, StringComparer.Ordinal)
            .Select(group => areaNames.TryGetValue(group.Key, out var name) ? new ReviewDigest.Area(name, group.Count()) : null)
            .FirstOrDefault(area => area is not null);

        var horizon = HorizonOf(kind);
        var goalList = goals.ToList();
        var entryList = entries.ToList();
        var periodGoals = goalList.Where(goal =>
            goal.Status != GoalRules.Dropped
            && goal.Horizon == horizon
            && GoalRules.PeriodEnd(goal.Horizon, goal.PeriodStart) >= periodStart
            && goal.PeriodStart <= end).ToList();
        var entriesByGoal = entryList.ToLookup(entry => entry.GoalId, StringComparer.Ordinal);
        var tasksByGoal = live.Where(task => task.GoalId is not null).ToLookup(task => task.GoalId!, StringComparer.Ordinal);
        var goalRows = periodGoals.Select(goal =>
        {
            var habitAmounts = goal.Mode == GoalRules.ModeNumber
                ? HabitRules.GoalAmounts(goal, habits, checkins).Select(amount => new GoalEntryItem(string.Empty, goal.Id, goal.PeriodStart, amount))
                : [];
            var progress = GoalRules.Progress(goal.Mode, goal.Status, goal.Target, tasksByGoal[goal.Id], [.. entriesByGoal[goal.Id], .. habitAmounts]);
            return new ReviewDigest.Goal(goal.Id, goal.Title, goal.Emoji, progress.Fraction, progress.Hit);
        }).ToList();

        var habitRows = habits.Where(habit => !habit.Archived).Select(habit =>
        {
            var own = checkins.Where(checkin => checkin.HabitId == habit.Id).ToList();
            var rests = pauses.Where(pause => pause.HabitId == habit.Id).ToList();
            var states = Periods(habit, periodStart, last).Select(start => HabitRules.State(habit, start, today, own, rests)).ToList();
            return new ReviewDigest.Habit(
                habit.Id,
                habit.Name,
                habit.Emoji,
                states.Count(state => state == HabitPeriodState.Met),
                states.Count(state => state != HabitPeriodState.None),
                HabitRules.Streak(habit, last, own, rests));
        }).ToList();

        var open = live.Where(task => task.State == TaskState.Open && task.PlannedDate is { } day && day >= periodStart && day <= end).ToList();
        var expected = Expected(periodStart, end, today);
        var facts = new PeriodFacts
        {
            DoneTasks = inPeriod.Count,
            AverageDone = doneBefore,
            Goals = [.. goalRows.Select(goal => new PeriodFacts.GoalFact(goal.Title, goal.Fraction, expected))],
            Habits = [.. habitRows.Select(habit => new PeriodFacts.HabitFact(habit.Name, habit.Periods - habit.Met, habit.Periods, habit.Streak))],
            Tasks = [.. open.Select(task => new PeriodFacts.TaskFact(task.Title, task.MovedCount))],
        };

        return new ReviewDigest
        {
            Kind = kind,
            PeriodStart = periodStart,
            PeriodEnd = end,
            Done = inPeriod.Count,
            DoneBefore = doneBefore,
            Days = days,
            BestDay = days.Where(day => day.Done > 0).OrderByDescending(day => day.Done).ThenBy(day => day.Date).FirstOrDefault(),
            StrongestArea = strongest,
            Goals = goalRows,
            Habits = habitRows,
            OpenTasks = open,
            Facts = facts,
        };
    }

    // The starts of the habit's periods inside the review's period.
    private static IEnumerable<DateOnly> Periods(HabitItem habit, DateOnly from, DateOnly to)
    {
        var start = HabitRules.PeriodStart(habit, from);
        while (start <= to)
        {
            if (start >= from || HabitRules.PeriodEnd(habit, start) >= from)
            {
                yield return start;
            }

            start = HabitRules.PeriodEnd(habit, start).AddDays(1);
        }
    }

    // The day a task was completed, by the server's timestamp.
    private static DateOnly? CompletedOn(TaskItem task) =>
        task.State == TaskState.Done && task.CompletedAt is { Length: >= 10 } stamp
            ? DateOnly.ParseExact(stamp[..10], "yyyy-MM-dd", CultureInfo.InvariantCulture)
            : null;
}

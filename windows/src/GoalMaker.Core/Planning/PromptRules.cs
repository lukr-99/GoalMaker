namespace GoalMaker.Core.Planning;

/// <summary>
/// Which prompts a review asks (docs/reviews.md, contracts/vectors/reviews.json): the rotation through
/// the library's categories, the prompts the period's facts call for, and the text of a prompt once the
/// period and the subject are filled in.
/// </summary>
public static class PromptRules
{
    // A goal this far from where it should be, a task moved this often and a streak this long are worth asking about.
    private const double GoalGap = 0.2;
    private const int SlippingMoves = 3;
    private const int LongStreak = 7;

    /// <summary>The prompts of the library that suit <paramref name="kind"/> and wait for no trigger, in the file's order.</summary>
    public static IReadOnlyList<ReviewPrompt> Library(PromptLibrary library, string kind) =>
        [.. library.Prompts.Where(prompt => prompt.Trigger is null && prompt.Reviews.Contains(kind))];

    /// <summary>
    /// The prompt of <paramref name="category"/> to ask next: the one shown longest ago, which is any it
    /// hasn't shown yet, in the file's order. Null when the category has nothing for this kind of review.
    /// </summary>
    public static ReviewPrompt? Next(PromptLibrary library, string kind, string category, IReadOnlyList<string> shown)
    {
        var last = LastSeen(shown);
        return Library(library, kind)
            .Where(prompt => prompt.Category == category)
            .Select((prompt, order) => (prompt, order))
            .OrderBy(entry => last.TryGetValue(entry.prompt.Id, out var index) ? index : -1)
            .ThenBy(entry => entry.order)
            .Select(entry => entry.prompt)
            .FirstOrDefault();
    }

    /// <summary>
    /// The <paramref name="count"/> prompts a review asks, walking the categories from the one after the
    /// last id in <paramref name="shown"/> and taking one from each (a category with nothing left to say
    /// is passed over).
    /// </summary>
    public static IReadOnlyList<ReviewPrompt> Rotation(PromptLibrary library, string kind, IReadOnlyList<string> shown, int count)
    {
        var suited = Library(library, kind);
        var categories = library.Categories.Where(category => suited.Any(prompt => prompt.Category == category)).ToList();
        if (categories.Count == 0 || count <= 0)
        {
            return [];
        }

        var lastCategory = shown.Count > 0 && library[shown[^1]] is { } lastPrompt ? lastPrompt.Category : null;
        var from = lastCategory is null ? 0 : categories.IndexOf(lastCategory) + 1;
        var chosen = new List<ReviewPrompt>();
        var seen = shown.ToList();
        for (var step = 0; step < categories.Count && chosen.Count < count; step++)
        {
            var category = categories[((from + step) % categories.Count + categories.Count) % categories.Count];
            if (Next(library, kind, category, seen) is not { } prompt)
            {
                continue;
            }

            chosen.Add(prompt);
            seen.Add(prompt.Id);
        }

        return chosen;
    }

    /// <summary>
    /// The triggered prompts <paramref name="facts"/> call for, worst first within each trigger: a goal
    /// behind plan, a habit mostly missed, a task that keeps moving, a long streak, a goal ahead of plan,
    /// a period without goals, and a quiet or busy period (not in a yearly review).
    /// </summary>
    public static IReadOnlyList<ReviewQuestion> Reactive(PromptLibrary library, string kind, PeriodFacts facts)
    {
        var triggers = library.Prompts
            .Where(prompt => prompt.Trigger is not null && prompt.Reviews.Contains(kind))
            .ToDictionary(prompt => prompt.Trigger!, StringComparer.Ordinal);
        var questions = new List<ReviewQuestion>();
        void Ask(string trigger, string? subject = null)
        {
            if (triggers.TryGetValue(trigger, out var prompt))
            {
                questions.Add(new ReviewQuestion(prompt.Id, Text(prompt, kind, subject), subject));
            }
        }

        var behind = facts.Goals
            .Where(goal => goal.Expected - goal.Fraction >= GoalGap)
            .OrderByDescending(goal => goal.Expected - goal.Fraction)
            .ThenBy(goal => goal.Title, StringComparer.Ordinal)
            .FirstOrDefault();
        if (behind is not null)
        {
            Ask("goal_behind", behind.Title);
        }

        var missed = facts.Habits
            .Where(habit => habit.Periods >= 2 && habit.Missed >= (int)Math.Ceiling(habit.Periods / 2.0))
            .OrderByDescending(habit => habit.Missed)
            .ThenBy(habit => habit.Name, StringComparer.Ordinal)
            .FirstOrDefault();
        if (missed is not null)
        {
            Ask("habit_missed", missed.Name);
        }

        var slipping = facts.Tasks
            .Where(task => task.Moves >= SlippingMoves)
            .OrderByDescending(task => task.Moves)
            .ThenBy(task => task.Title, StringComparer.Ordinal)
            .FirstOrDefault();
        if (slipping is not null)
        {
            Ask("task_slipping", slipping.Title);
        }

        var streak = facts.Habits
            .Where(habit => habit.Streak >= LongStreak)
            .OrderByDescending(habit => habit.Streak)
            .ThenBy(habit => habit.Name, StringComparer.Ordinal)
            .FirstOrDefault();
        if (streak is not null)
        {
            Ask("habit_streak", streak.Name);
        }

        var ahead = facts.Goals
            .Where(goal => goal.Fraction - goal.Expected >= GoalGap)
            .OrderByDescending(goal => goal.Fraction - goal.Expected)
            .ThenBy(goal => goal.Title, StringComparer.Ordinal)
            .FirstOrDefault();
        if (ahead is not null)
        {
            Ask("goal_ahead", ahead.Title);
        }

        if (facts.Goals.Count == 0)
        {
            Ask("no_goals");
        }

        if (facts.AverageDone > 0)
        {
            if (facts.DoneTasks <= facts.AverageDone / 2)
            {
                Ask("quiet_period");
            }

            if (facts.DoneTasks >= facts.AverageDone * 1.5)
            {
                Ask("busy_period");
            }
        }

        return questions;
    }

    /// <summary>A prompt's text for a <paramref name="kind"/> review, with the period named and the subject filled in.</summary>
    public static string Text(ReviewPrompt prompt, string kind, string? subject = null) =>
        prompt.Text.Replace("{period}", Period(kind), StringComparison.Ordinal)
            .Replace("{subject}", subject ?? string.Empty, StringComparison.Ordinal);

    /// <summary>What a review of <paramref name="kind"/> calls its period: week, month or year (the kinds of ReviewRules).</summary>
    public static string Period(string kind) => kind switch
    {
        "monthly" => "month",
        "yearly" => "year",
        _ => "week",
    };

    // Where each id was shown last, so a prompt never shown comes first.
    private static Dictionary<string, int> LastSeen(IReadOnlyList<string> shown)
    {
        var last = new Dictionary<string, int>(StringComparer.Ordinal);
        for (var index = 0; index < shown.Count; index++)
        {
            last[shown[index]] = index;
        }

        return last;
    }
}

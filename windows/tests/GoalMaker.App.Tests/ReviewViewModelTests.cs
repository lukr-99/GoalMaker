using System.IO;
using System.Text.Json.Nodes;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.Tests;

/// <summary>The Windows review over a real replica: the look back, the prompts, the ratings and the next goals (M4-06).</summary>
public sealed class ReviewViewModelTests : IDisposable
{
    // Monday 21 September 2026: the week under review is 14 to 20 September.
    private static readonly DateOnly Today = new(2026, 9, 21);
    private static readonly DateOnly WeekStart = new(2026, 9, 14);
    private readonly TestPlanner planner = new();
    private readonly PromptLibrary prompts = PromptLibrary.Parse(File.ReadAllText(PromptsPath()));

    public ReviewViewModelTests() => planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 21, 9, 0, 0, TimeSpan.Zero));

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheLookBackCountsThePeriodsTasksAgainstTheOneBefore()
    {
        Done("Monday thing", WeekStart);
        Done("Tuesday thing", WeekStart.AddDays(1));
        Done("Tuesday second", WeekStart.AddDays(1));
        Done("Last week", WeekStart.AddDays(-3));
        var page = Page();

        Assert.Equal((3, 1, 2), (page.Digest.Done, page.Digest.DoneBefore, page.Digest.Change));
        Assert.Equal(WeekStart.AddDays(1), page.Digest.BestDay!.Date);
        Assert.Equal(7, page.Days.Count);
        Assert.Equal("Reviews.Done(3)", page.DoneText);
        Assert.Equal("Reviews.MoreThanBefore(2)", page.ChangeText);
    }

    [Fact]
    public void ThePromptsAreAskedAndTheAnswersAreKept()
    {
        var page = Page();

        Assert.Equal(3, page.QuestionRows.Count);
        Assert.DoesNotContain(page.QuestionRows, question => question.Text.Contains("{period}", StringComparison.Ordinal));

        page.QuestionRows[0].Answer = "A good week.";
        page.SaveAnswers();

        var saved = planner.Reviews.Find(ReviewRules.Weekly, WeekStart)!;
        Assert.Equal(3, saved.Reflections.Count);
        Assert.Equal("A good week.", saved.Reflections[0].Answer);
        Assert.True(saved.Written);
    }

    [Fact]
    public void APeriodThatCallsForItGetsAReactivePromptFirst()
    {
        var goal = planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Week, WeekStart, GoalRules.ModeNumber, Target: 80, Unit: "km"))!;
        planner.Goals.LogAmount(goal.Id, WeekStart, 5);
        var page = Page();

        Assert.Equal("goals/behind", page.QuestionRows[0].PromptId);
        Assert.Contains("Run 80 km", page.QuestionRows[0].Text, StringComparison.Ordinal);
    }

    [Fact]
    public void MoodAndEnergyAreSavedAndCanBeTakenBack()
    {
        var page = Page();

        page.SetMood(4);
        page.SetEnergy(2);
        Assert.Equal((4, 2), (page.Mood, page.Energy));
        Assert.Equal(4, planner.Reviews.Find(ReviewRules.Weekly, WeekStart)!.Mood);

        page.SetMood(4);
        Assert.Null(page.Mood);
        Assert.Null(planner.Reviews.Find(ReviewRules.Weekly, WeekStart)!.Mood);
    }

    [Fact]
    public void TasksLeftOpenMoveOnAreDoneOrAreDropped()
    {
        var open = Add("Call the bank");
        planner.Tasks.Plan(open.Id, WeekStart.AddDays(2));
        var second = Add("Fix the bike");
        planner.Tasks.Plan(second.Id, WeekStart.AddDays(3));
        var page = Page();

        Assert.True(page.HasOpenTasks);
        page.OpenTasks.First(row => row.Title == "Call the bank").ForwardCommand.Execute(null);
        page.OpenTasks.First(row => row.Title == "Fix the bike").DropCommand.Execute(null);

        Assert.Equal(new DateOnly(2026, 9, 21), planner.Tasks.All().First(task => task.Title == "Call the bank").PlannedDate);
        Assert.Equal(TaskState.Dropped, planner.Tasks.All().First(task => task.Title == "Fix the bike").State);
    }

    [Fact]
    public void TheLastStepRecordsTheRitualAndTheNextPeriodsGoalsCanBeCopied()
    {
        planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, WeekStart));
        var page = Page();

        while (!page.IsDone)
        {
            page.NextCommand.Execute(null);
        }

        Assert.Contains(Today, planner.Rituals.Ran(RitualRunList.WeeklyReview));

        page.CopyGoalsCommand.Execute(null);
        Assert.Equal(["3 runs"], planner.Goals.All()
            .Where(goal => goal.Horizon == GoalHorizon.Week && goal.PeriodStart == WeekStart.AddDays(7))
            .Select(goal => goal.Title));
    }

    [Fact]
    public void AReviewOpenedAgainShowsThePromptsItAskedBefore()
    {
        var first = Page();
        var asked = first.QuestionRows.Select(question => question.PromptId).ToList();
        first.QuestionRows[1].Answer = "Something learned.";
        first.SaveAnswers();

        var again = Page();
        Assert.Equal(asked, again.QuestionRows.Select(question => question.PromptId));
        Assert.Equal("Something learned.", again.QuestionRows[1].Answer);
    }

    [Fact]
    public void AMonthlyReviewLooksAtTheMonthAndItsGoals()
    {
        var monthStart = new DateOnly(2026, 9, 1);
        planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, monthStart, GoalRules.ModeNumber, Target: 80, Unit: "km"));
        planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, WeekStart));
        planner.Habits.Add(new HabitDraft("Read", monthStart));
        Done("Something", monthStart.AddDays(4));
        var page = Page(ReviewRules.Monthly, monthStart);

        Assert.Equal(["Run 80 km"], page.PeriodGoals.Select(goal => goal.Title));
        Assert.Equal(new DateOnly(2026, 9, 30), page.Digest.PeriodEnd);
        Assert.Equal(1, page.Digest.Done);
        Assert.Contains(page.PeriodHabits, habit => habit.Name == "Read");
        Assert.Equal("Reviews.Monthly", page.Heading);
    }

    [Fact]
    public void TheReviewsPageListsWhatIsWaitingAndWhatWasWritten()
    {
        var opened = new List<(string Kind, DateOnly Start)>();
        var list = new ReviewsViewModel(planner.Reviews, planner.Settings, planner.Strings, planner.Time, (kind, start) => opened.Add((kind, start)), action => action());

        Assert.Equal(4, list.ToWrite.Count);
        Assert.True(list.IsEmpty);

        var page = Page();
        page.SetMood(5);
        list.Refresh();

        Assert.False(list.IsEmpty);
        Assert.Equal("Reviews.MoodShort(5)", list.Past.Single().Subtitle);
        list.Past.Single().OpenCommand.Execute(null);
        Assert.Equal((ReviewRules.Weekly, WeekStart), opened.Single());

        list.Past.Single().DeleteCommand.Execute(null);
        list.Refresh();
        Assert.True(list.IsEmpty);
    }

    private static string PromptsPath()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "contracts")))
        {
            directory = directory.Parent;
        }

        return Path.Combine(directory!.FullName, "contracts", "content", "prompts.json");
    }

    private ReviewViewModel Page(string kind = ReviewRules.Weekly, DateOnly? start = null) => new(
        kind,
        start ?? WeekStart,
        planner.Reviews,
        planner.Tasks,
        planner.Areas,
        planner.Goals,
        planner.Habits,
        prompts,
        planner.Rituals,
        planner.Settings,
        planner.Strings,
        planner.Time,
        action => action());

    private TaskItem Add(string line)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Time.Advance(TimeSpan.FromSeconds(1));
        return task;
    }

    private void Done(string title, DateOnly day)
    {
        var task = Add(title);
        planner.Tasks.SetDone(task.Id, true);
        // The replica keeps the server's completion time; the test writes the day it happened.
        if (planner.Replica.Get("tasks", task.Id) is { } row)
        {
            row["completed_at"] = JsonValue.Create($"{day:yyyy-MM-dd}T18:00:00.000000Z");
            planner.Replica.Queue("tasks", row);
        }
    }
}

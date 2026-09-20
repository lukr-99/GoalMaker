using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>
/// Every list that writes rows fills the columns the server needs a value in
/// (contracts/schemas/synced-tables.json). A row carries every column, so a null in one of those is
/// refused on the push whatever default the column has, and the apps' own tests never meet a server.
/// This walks all fifteen synced tables, so a table nobody writes here is caught too.
/// </summary>
public sealed class RequiredColumnsTests : IDisposable
{
    private static readonly DateOnly Day = new(2026, 9, 18);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));

    public void Dispose() => test.Dispose();

    [Fact]
    public void EveryListWritesRowsTheServerCanTake()
    {
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        var areas = new AreaList(test.Replica, rows, ["violet", "blue"], () => { });
        var tags = new TagList(test.Replica, rows, () => { });
        var steps = new StepList(test.Replica, rows, () => { });
        var goals = new GoalList(test.Replica, rows, () => { });
        var habits = new HabitList(test.Replica, rows, () => { });
        var reviews = new ReviewList(test.Replica, rows, () => { });
        var rituals = new RitualRunList(test.Replica, rows, () => { });
        var projects = new ProjectList(test.Replica, rows, () => { });
        var reminders = new ReminderList(test.Replica, rows, () => { }, () => TimeZoneInfo.Utc);
        var tasks = new TaskList(test.Replica, rows, areas, tags, projects, () => { }, () => Day);

        // One row in every synced table, each through the list that owns it.
        Assert.NotNull(areas.Create("Health"));
        Assert.NotNull(tags.FindOrCreate("errand"));
        var task = tasks.Add(ComposerParser.Parse("Call the bank 17:00 #errand @Health", new DateTime(2026, 9, 18, 9, 0, 0)));
        Assert.NotNull(task);
        Assert.NotNull(steps.Add(task.Id, "Find the number"));
        Assert.NotNull(reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 16, 45, 0)));
        var goal = goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Month, Day, GoalRules.ModeNumber, Target: 20, Unit: "km"));
        Assert.NotNull(goal);
        Assert.NotNull(goals.LogAmount(goal.Id, Day, 5));
        var habit = habits.Add(new HabitDraft("Read before bed", Day));
        Assert.NotNull(habit);
        Assert.NotNull(habits.CheckIn(habit.Id, Day));
        Assert.True(habits.Pause(habit.Id, Day));
        var project = projects.Add(new ProjectDraft("GoalMaker"));
        Assert.NotNull(project);
        Assert.NotNull(projects.AddMilestone(project.Id, "M6"));
        rituals.Record(RitualRunList.PlanTomorrow, Day);
        Assert.NotNull(reviews.Open(ReviewRules.Weekly, Day));

        // Every table was written, and every row of every table fills what the server needs.
        foreach (var table in test.Catalog.Tables)
        {
            var stored = test.Replica.All(table.Name);
            Assert.True(stored.Count > 0, $"{table.Name} needs a row here");
            Assert.True(table.Required.Count > 0, $"{table.Name} describes no required column");
            foreach (var row in stored)
            {
                foreach (var column in table.Required)
                {
                    Assert.True(row[column] is not null, $"{table.Name}.{column} needs a value, or the server refuses the row");
                }
            }
        }
    }
}

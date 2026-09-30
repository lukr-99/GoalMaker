using System.Text.Json.Nodes;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Tests.Sync;
using GoalMaker.Infrastructure.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Tally's days, categories and rules on a real replica (docs/tally.md, M8-10).</summary>
public sealed class TallyListTests : IDisposable
{
    private const string Device = "D1E57000-0000-4000-8000-00000000AAAA";
    private static readonly DateOnly Day = new(2026, 9, 28);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 28, 12, 0, 0, TimeSpan.Zero));
    private readonly NewRows rows;
    private readonly TallyList tally;
    private int syncs;

    public TallyListTests()
    {
        rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        tally = new TallyList(test.Replica, rows, () => Device, () => syncs++);
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void ADaysTotalsGoInRowsTheirIdsName()
    {
        Assert.True(tally.RewriteDay(Day, [new TallyTotal(Day, "coding", "p-goalmaker", 90), new TallyTotal(Day, "video", null, 30)]));

        var days = tally.Days(Day, Day);
        Assert.Equal(["coding", "video"], days.Select(day => day.Category));
        Assert.Equal(TallyRules.DayId(TestReplica.Owner, Day, Device, "coding", "p-goalmaker"), days[0].Id);
        Assert.Equal(TallyRules.Pc, days[0].DeviceKind);
        Assert.Equal(Device, days[0].Device);
        Assert.Equal(90, days[0].Minutes);
        Assert.Equal(2, test.Replica.PendingCount());
    }

    [Fact]
    public void RewritingADayReplacesThisDevicesRowsAndLeavesOthersAlone()
    {
        var phone = rows.Create("tally_days", new Dictionary<string, JsonNode?>
        {
            ["day"] = "2026-09-28",
            ["device"] = "e2e57000-0000-4000-8000-00000000bbbb",
            ["device_kind"] = TallyRules.Phone,
            ["category"] = "video",
            ["minutes"] = 45,
        })!;
        test.Replica.Put("tally_days", phone);
        tally.RewriteDay(Day, [new TallyTotal(Day, "coding", null, 90), new TallyTotal(Day, "chat", null, 10)]);
        tally.RewriteDay(Day.AddDays(1), [new TallyTotal(Day.AddDays(1), "games", null, 20)]);

        Assert.True(tally.RewriteDay(Day, [new TallyTotal(Day, "coding", null, 95), new TallyTotal(Day.AddDays(1), "reading", null, 5)]));

        Assert.Equal(
            [("coding", 95, Device), ("video", 45, "e2e57000-0000-4000-8000-00000000bbbb")],
            tally.Days(Day, Day).Select(day => (day.Category, day.Minutes, day.Device)));
        Assert.Equal(["games"], tally.Days(Day.AddDays(1), Day.AddDays(1)).Select(day => day.Category));
        var chat = test.Replica.Get("tally_days", TallyRules.DayId(TestReplica.Owner, Day, Device, "chat", null))!;
        Assert.NotNull(chat[SyncedTable.DeletedAt]);
    }

    [Fact]
    public void AnUnchangedDayWritesNothing()
    {
        tally.RewriteDay(Day, [new TallyTotal(Day, "coding", null, 90)]);
        var before = syncs;

        tally.RewriteDay(Day, [new TallyTotal(Day, "coding", null, 90)]);

        Assert.Equal(before, syncs);
    }

    [Fact]
    public void CategoriesAndRulesAreAddedInOrder()
    {
        Assert.NotNull(tally.AddCategory(" Chess ", "Teal", "♟"));
        Assert.NotNull(tally.AddCategory("Music", "orange"));
        Assert.Null(tally.AddCategory("  ", "teal"));
        Assert.Null(tally.AddCategory("Art", "not a color"));
        Assert.Equal([("Chess", "teal"), ("Music", "orange")], tally.Categories().Select(category => (category.Name, category.Color)));

        var chess = tally.Categories()[0].Id;
        Assert.NotNull(tally.AddRule(new TallyRule(TallyRules.Title, " lichess ", TallyRules.Windows, chess)));
        Assert.NotNull(tally.AddRule(new TallyRule(TallyRules.App, "org.lichess.mobileapp", TallyRules.Android, chess)));
        Assert.Null(tally.AddRule(new TallyRule(TallyRules.Title, "lichess", TallyRules.Android, chess)));
        Assert.Null(tally.AddRule(new TallyRule("window", "lichess", TallyRules.Any, chess)));
        Assert.Null(tally.AddRule(new TallyRule(TallyRules.App, " ", TallyRules.Any, chess)));

        var own = tally.Rules();
        Assert.Equal(["lichess", "org.lichess.mobileapp"], own.Select(rule => rule.Pattern));
        var sort = TallyRules.SortSample(
            new TallySample(TallyRules.Windows, "firefox.exe", "Puzzle - lichess.org - Mozilla Firefox"), own, ContractResources.TallyDefaults().Rules);
        Assert.Equal(new TallySort(chess, null), sort);
    }
}

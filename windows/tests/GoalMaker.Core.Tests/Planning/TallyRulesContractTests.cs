using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/tally.json, the same file the Android tests and the connector read.</summary>
public sealed class TallyRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/tally.json").RootElement;
    private readonly TallyDefaults shipped = TallyDefaults.Parse(ContractFiles.Load("content/tally-rules.json").RootElement.GetRawText());

    [Fact]
    public void TheAppShipsTheRulesFile()
    {
        var built = ContractResources.TallyDefaults();
        Assert.Equal(shipped.Categories, built.Categories);
        Assert.Equal(shipped.Rules, built.Rules);
        Assert.Contains(shipped.Categories, category => category.Id == TallyRules.Other);
        Assert.Contains(shipped.Categories, category => category.Id == TallyRules.Video);
    }

    [Fact]
    public void EveryMatch()
    {
        foreach (var testCase in vectors.GetProperty("match").EnumerateArray())
        {
            var sample = testCase.GetProperty("sample");
            var defaults = testCase.GetProperty("defaults");
            var sort = TallyRules.SortSample(
                new TallySample(sample.GetProperty("platform").GetString()!, sample.GetProperty("app").GetString()!, Text(sample, "title")),
                testCase.TryGetProperty("own", out var own) ? Rules(own) : [],
                defaults.ValueKind == JsonValueKind.String ? shipped.Rules : Rules(defaults),
                testCase.TryGetProperty("projects", out var projects) ? Projects(projects) : []);
            var expect = testCase.GetProperty("expect");
            Assert.True(new TallySort(expect.GetProperty("category").GetString()!, Text(expect, "project")) == sort, Name(testCase));
        }
    }

    [Fact]
    public void EveryEditorFolder()
    {
        foreach (var testCase in vectors.GetProperty("folder").EnumerateArray())
        {
            var actual = TallyRules.EditorFolder(testCase.GetProperty("app").GetString()!, Text(testCase, "title"));
            Assert.True(Text(testCase, "expect") == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryProject()
    {
        foreach (var testCase in vectors.GetProperty("project").EnumerateArray())
        {
            var actual = TallyRules.ProjectFor(Text(testCase, "folder"), Projects(testCase.GetProperty("projects")));
            Assert.True(Text(testCase, "expect") == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryIdleMoment()
    {
        foreach (var testCase in vectors.GetProperty("idle").EnumerateArray())
        {
            var actual = TallyRules.Counts(
                testCase.GetProperty("secondsSinceInput").GetInt32(),
                testCase.GetProperty("category").GetString()!,
                testCase.GetProperty("locked").GetBoolean(),
                testCase.GetProperty("asleep").GetBoolean());
            Assert.True(testCase.GetProperty("expect").GetBoolean() == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryDay()
    {
        foreach (var testCase in vectors.GetProperty("days").EnumerateArray())
        {
            var intervals = testCase.GetProperty("intervals").EnumerateArray().Select(interval => new TallyInterval(
                Moment(interval, "start"),
                Moment(interval, "end"),
                interval.GetProperty("category").GetString()!,
                Text(interval, "project")));
            var actual = TallyRules.DayTotals(intervals, testCase.GetProperty("startHour").GetInt32());
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(total => new TallyTotal(
                Day(total, "day"),
                total.GetProperty("category").GetString()!,
                Text(total, "project"),
                total.GetProperty("minutes").GetInt32()));
            Assert.True(expected.SequenceEqual(actual), Name(testCase));
        }
    }

    [Fact]
    public void EveryDayId()
    {
        Assert.Equal("b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91", vectors.GetProperty("namespace").GetString());
        foreach (var testCase in vectors.GetProperty("ids").EnumerateArray())
        {
            var actual = TallyRules.DayId(
                testCase.GetProperty("owner").GetString()!,
                Day(testCase, "day"),
                testCase.GetProperty("device").GetString()!,
                testCase.GetProperty("category").GetString()!,
                Text(testCase, "project"));
            Assert.Equal(testCase.GetProperty("expect").GetString(), actual);
        }
    }

    [Fact]
    public void EveryWeeksBlock()
    {
        foreach (var testCase in vectors.GetProperty("weeks").EnumerateArray())
        {
            var filter = testCase.GetProperty("filter");
            var rows = testCase.GetProperty("rows").EnumerateArray().Select(row => new TallyDay(
                string.Empty,
                Day(row, "day"),
                string.Empty,
                row.GetProperty("deviceKind").GetString()!,
                row.GetProperty("category").GetString()!,
                Text(row, "projectId"),
                row.GetProperty("minutes").GetInt32()));
            var actual = TallyRules.Weeks(
                rows, Day(testCase, "today"), testCase.GetProperty("count").GetInt32(), Text(filter, "kind"), Text(filter, "category"));
            var expected = testCase.GetProperty("expect").EnumerateArray().ToList();
            Assert.True(expected.Count == actual.Count, Name(testCase));
            foreach (var (week, want) in actual.Zip(expected))
            {
                Assert.True(Day(want, "start") == week.Start, Name(testCase));
                Assert.True(want.GetProperty("minutes").GetInt32() == week.Minutes, Name(testCase));
                var categories = want.GetProperty("categories").EnumerateArray()
                    .Select(category => new TallyMinutes(category.GetProperty("category").GetString()!, category.GetProperty("minutes").GetInt32()));
                Assert.True(categories.SequenceEqual(week.Categories), Name(testCase));
            }
        }
    }

    [Fact]
    public void EveryWindowLabel()
    {
        foreach (var testCase in vectors.GetProperty("window").EnumerateArray())
        {
            var actual = TallyBreakdown.WindowLabel(testCase.GetProperty("app").GetString()!, Text(testCase, "title"));
            Assert.True(Text(testCase, "expect") == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryDayByTheHour()
    {
        foreach (var testCase in vectors.GetProperty("hours").EnumerateArray())
        {
            var startHour = testCase.GetProperty("startHour").GetInt32();
            var hours = TallyBreakdown.Hours(Stretches(testCase), Day(testCase, "day"), startHour);
            Assert.True(Enumerable.Range(0, 24).Select(index => (startHour + index) % 24).SequenceEqual(hours.Select(hour => hour.Hour)), Name(testCase));
            Assert.True(hours.Where(hour => hour.Seconds == 0).All(hour => hour.Categories.Count == 0), Name(testCase));
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(hour =>
                $"{hour.GetProperty("hour").GetInt32()} {hour.GetProperty("seconds").GetInt32()}: " +
                string.Join(", ", hour.GetProperty("categories").EnumerateArray().Select(part => $"{part.GetProperty("category").GetString()} {part.GetProperty("seconds").GetInt32()}")));
            var actual = hours.Where(hour => hour.Seconds > 0).Select(hour =>
                $"{hour.Hour} {hour.Seconds}: " + string.Join(", ", hour.Categories.Select(part => $"{part.Category} {part.Seconds}")));
            Assert.Equal(expected, actual);
        }
    }

    [Fact]
    public void EveryAppList()
    {
        foreach (var testCase in vectors.GetProperty("apps").EnumerateArray())
        {
            var actual = TallyBreakdown.Apps(Stretches(testCase), Day(testCase, "from"), Day(testCase, "to"), testCase.GetProperty("startHour").GetInt32());
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(group =>
                $"{group.GetProperty("category").GetString()} {group.GetProperty("minutes").GetInt32()} [" +
                string.Join("; ", group.GetProperty("apps").EnumerateArray().Select(app =>
                    $"{app.GetProperty("app").GetString()} {app.GetProperty("minutes").GetInt32()} (" +
                    string.Join(", ", app.GetProperty("windows").EnumerateArray().Select(window => $"{window.GetProperty("label").GetString()} {window.GetProperty("minutes").GetInt32()}")) + ")")) + "]");
            var described = actual.Select(group =>
                $"{group.Category} {group.Minutes} [" +
                string.Join("; ", group.Apps.Select(app =>
                    $"{app.App} {app.Minutes} (" + string.Join(", ", app.Windows.Select(window => $"{window.Label} {window.Minutes}")) + ")")) + "]");
            Assert.Equal(expected, described);
        }
    }

    [Fact]
    public void EveryShippedCategoryHasItsOwnColor()
    {
        Assert.Equal(shipped.Categories.Count, shipped.Categories.Select(category => category.Color).Distinct().Count());
        Assert.Equal(TallyRules.Other, shipped.Categories[^1].Id);
    }

    private static List<TallyStretch> Stretches(JsonElement testCase) =>
    [
        .. testCase.GetProperty("stretches").EnumerateArray().Select(stretch => new TallyStretch(
            Moment(stretch, "start"),
            Moment(stretch, "end"),
            stretch.GetProperty("app").GetString()!,
            Text(stretch, "title"),
            stretch.GetProperty("category").GetString()!)),
    ];

    private static List<TallyRule> Rules(JsonElement value) =>
    [
        .. value.EnumerateArray().Select(rule => new TallyRule(
            rule.GetProperty("match").GetString()!,
            rule.GetProperty("pattern").GetString()!,
            rule.GetProperty("platform").GetString()!,
            rule.GetProperty("category").GetString()!,
            Text(rule, "project"))),
    ];

    private static List<ProjectItem> Projects(JsonElement value) =>
    [
        .. value.EnumerateArray().Select(project =>
            new ProjectItem(project.GetProperty("id").GetString()!, "Project") { LocalFolder = Text(project, "localFolder") }),
    ];

    private static DateTime Moment(JsonElement value, string name) =>
        DateTime.Parse(value.GetProperty(name).GetString()!, CultureInfo.InvariantCulture);

    private static DateOnly Day(JsonElement value, string name) =>
        DateOnly.ParseExact(value.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);

    private static string? Text(JsonElement value, string name) =>
        value.TryGetProperty(name, out var text) && text.ValueKind == JsonValueKind.String ? text.GetString() : null;

    private static string Name(JsonElement testCase) => testCase.GetProperty("name").GetString()!;
}

using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/projects.json, the same file the Android tests and the connector read.</summary>
public sealed class ProjectRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/projects.json").RootElement;

    [Fact]
    public void TheColumnsAndPrioritiesAreTheOnesTheContractNames()
    {
        Assert.Equal(vectors.GetProperty("columns").EnumerateArray().Select(name => name.GetString()), ProjectRules.Columns);
        Assert.Equal(vectors.GetProperty("priorities").EnumerateArray().Select(name => name.GetString()), ProjectRules.Priorities);
    }

    [Fact]
    public void EveryNewItemLandsInItsColumn()
    {
        foreach (var testCase in vectors.GetProperty("newItems").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").GetString() == ProjectRules.ColumnFor(testCase.GetProperty("type").GetString()!),
                Name(testCase));
        }
    }

    [Fact]
    public void EveryMove()
    {
        foreach (var testCase in vectors.GetProperty("moves").EnumerateArray())
        {
            var expect = testCase.GetProperty("expect");
            var column = testCase.GetProperty("column").GetString()!;
            Assert.True(expect.GetProperty("column").GetString() == column, Name(testCase));
            Assert.True(
                State(expect.GetProperty("state").GetString()) == ProjectRules.Moved(column, State(testCase.GetProperty("state").GetString())),
                Name(testCase));
        }
    }

    [Fact]
    public void EveryFinish()
    {
        foreach (var testCase in vectors.GetProperty("finishing").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").GetString() ==
                ProjectRules.FinishedIn(State(testCase.GetProperty("state").GetString()), testCase.GetProperty("column").GetString()!),
                Name(testCase));
        }
    }

    [Fact]
    public void EveryOrder()
    {
        foreach (var testCase in vectors.GetProperty("order").EnumerateArray())
        {
            Assert.Equal(
                testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()),
                ProjectRules.Order(Items(testCase)).Select(item => item.Id));
        }
    }

    [Fact]
    public void EveryBoard()
    {
        foreach (var testCase in vectors.GetProperty("board").EnumerateArray())
        {
            var board = ProjectRules.Board(Items(testCase));
            Assert.Equal(ProjectRules.Columns, board.Select(column => column.Column));
            foreach (var column in board)
            {
                Assert.Equal(
                    testCase.GetProperty("expect").GetProperty(column.Column).EnumerateArray().Select(id => id.GetString()),
                    column.Items.Select(item => item.Id));
            }
        }
    }

    [Fact]
    public void EveryMakerFilter()
    {
        var makers = vectors.GetProperty("makers");
        Assert.Equal(makers.GetProperty("values").EnumerateArray().Select(value => value.GetString()), ProjectRules.Makers);
        Assert.Equal(makers.GetProperty("filters").EnumerateArray().Select(value => value.GetString()), ProjectRules.MakerFilters);
        foreach (var testCase in makers.GetProperty("cases").EnumerateArray())
        {
            var filter = testCase.GetProperty("filter").GetString()!;
            var shown = testCase.GetProperty("items").EnumerateArray()
                .Where(item => ProjectRules.Shows(filter, item.TryGetProperty("madeBy", out var madeBy) ? madeBy.GetString() : null))
                .Select(item => item.GetProperty("id").GetString());
            Assert.Equal(testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()), shown);
        }
    }

    private static IReadOnlyList<TaskItem> Items(JsonElement testCase) =>
    [
        .. testCase.GetProperty("items").EnumerateArray().Select(item => new TaskItem(
            item.GetProperty("id").GetString()!,
            item.GetProperty("id").GetString()!,
            State(item.GetProperty("state").GetString()),
            false,
            item.GetProperty("createdAt").GetString()!)
        {
            ProjectId = "p",
            BoardColumn = item.GetProperty("column").GetString(),
            Priority = item.GetProperty("priority").GetString()!,
            Position = item.GetProperty("position").GetDouble(),
        }),
    ];

    private static TaskState State(string? name) => name switch
    {
        "done" => TaskState.Done,
        "dropped" => TaskState.Dropped,
        _ => TaskState.Open,
    };

    private static string Name(JsonElement testCase) =>
        testCase.TryGetProperty("name", out var name) ? name.GetString() ?? string.Empty : string.Empty;
}

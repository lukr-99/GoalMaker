using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Problems;

namespace GoalMaker.Core.Tests.Problems;

/// <summary>contracts/vectors/problems.json, the same file the Android tests read.</summary>
public sealed class ProblemRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/problems.json").RootElement;

    [Fact]
    public void BothAppsKnowTheSameKinds() =>
        Assert.Equal(
            vectors.GetProperty("kinds").EnumerateArray().Select(kind => kind.GetString()).Order(),
            ProblemRules.Kinds.Order());

    [Fact]
    public void EveryRunLeavesWhatTheContractSays()
    {
        foreach (var run in vectors.GetProperty("runs").EnumerateArray())
        {
            var name = run.GetProperty("name").GetString();
            IReadOnlyList<Problem> problems = [];
            foreach (var step in run.GetProperty("steps").EnumerateArray())
            {
                problems = Apply(problems, step);
            }

            var expected = run.GetProperty("expect");
            Assert.Equal($"{name}: {Show(expected)}", $"{name}: {Show(problems)}");
            Assert.Equal(
                $"{name}: marked {expected.GetProperty("marked").GetBoolean()}",
                $"{name}: marked {ProblemRules.IsMarked(problems)}");
        }
    }

    private static IReadOnlyList<Problem> Apply(IReadOnlyList<Problem> problems, JsonElement step)
    {
        if (step.TryGetProperty("report", out var kind))
        {
            var detail = step.GetProperty("detail");
            return ProblemRules.Report(
                problems,
                kind.GetString()!,
                When(step.GetProperty("at").GetString()!),
                detail.ValueKind == JsonValueKind.Null ? null : detail.GetString());
        }

        return step.TryGetProperty("clear", out var cleared)
            ? ProblemRules.Clear(problems, cleared.GetString()!)
            : ProblemRules.Read(problems);
    }

    private static string Show(IEnumerable<Problem> problems) =>
        string.Join(" | ", problems.Select(problem =>
            $"{problem.Kind} {problem.At:O} {problem.Detail ?? "-"} unread={problem.Unread}"));

    private static string Show(JsonElement expected) =>
        string.Join(" | ", expected.GetProperty("problems").EnumerateArray().Select(problem =>
        {
            var detail = problem.GetProperty("detail");
            return $"{problem.GetProperty("kind").GetString()} {When(problem.GetProperty("at").GetString()!):O} "
                + $"{(detail.ValueKind == JsonValueKind.Null ? "-" : detail.GetString())} unread={problem.GetProperty("unread").GetBoolean()}";
        }));

    private static DateTimeOffset When(string text) =>
        DateTimeOffset.Parse(text, CultureInfo.InvariantCulture, DateTimeStyles.AdjustToUniversal | DateTimeStyles.AssumeUniversal);
}

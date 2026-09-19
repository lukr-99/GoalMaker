using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>The rotation and the reactive prompts of contracts/vectors/reviews.json, over the shipped library.</summary>
public sealed class PromptRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/reviews.json").RootElement;
    private readonly PromptLibrary library = PromptLibrary.Parse(ContractFiles.Load("content/prompts.json").RootElement.GetRawText());

    [Fact]
    public void TheLibraryHasEveryPromptTheVectorsName()
    {
        var named = vectors.GetProperty("rotation").EnumerateArray().SelectMany(Ids)
            .Concat(vectors.GetProperty("promptTexts").EnumerateArray().Select(testCase => testCase.GetProperty("prompt").GetString()!))
            .Concat(vectors.GetProperty("reactive").EnumerateArray()
                .SelectMany(testCase => testCase.GetProperty("expect").EnumerateArray())
                .Select(question => question.GetProperty("prompt").GetString()!));
        foreach (var id in named)
        {
            Assert.True(library[id] is not null, $"{id} is missing from prompts.json");
        }

        Assert.True(library.Prompts.Count >= 100, "the library should hold about a hundred prompts");
    }

    [Fact]
    public void EveryRotation()
    {
        foreach (var testCase in vectors.GetProperty("rotation").EnumerateArray())
        {
            var kind = testCase.GetProperty("kind").GetString()!;
            var shown = testCase.GetProperty("shown").EnumerateArray().Select(id => id.GetString()!).ToList();
            var chosen = testCase.TryGetProperty("category", out var category)
                ? (IReadOnlyList<ReviewPrompt>)[.. new[] { PromptRules.Next(library, kind, category.GetString()!, shown) }.OfType<ReviewPrompt>()]
                : PromptRules.Rotation(library, kind, shown, testCase.GetProperty("count").GetInt32());
            Assert.Equal(Ids(testCase), chosen.Select(prompt => prompt.Id));
        }
    }

    [Fact]
    public void EveryReactivePrompt()
    {
        foreach (var testCase in vectors.GetProperty("reactive").EnumerateArray())
        {
            var facts = testCase.GetProperty("facts");
            var questions = PromptRules.Reactive(library, testCase.GetProperty("kind").GetString()!, new PeriodFacts
            {
                DoneTasks = facts.GetProperty("doneTasks").GetInt32(),
                AverageDone = facts.GetProperty("averageDone").GetDouble(),
                Goals = [.. facts.GetProperty("goals").EnumerateArray().Select(goal => new PeriodFacts.GoalFact(
                    goal.GetProperty("title").GetString()!,
                    goal.GetProperty("fraction").GetDouble(),
                    goal.GetProperty("expected").GetDouble()))],
                Habits = [.. facts.GetProperty("habits").EnumerateArray().Select(habit => new PeriodFacts.HabitFact(
                    habit.GetProperty("name").GetString()!,
                    habit.GetProperty("missed").GetInt32(),
                    habit.GetProperty("periods").GetInt32(),
                    habit.GetProperty("streak").GetInt32()))],
                Tasks = [.. facts.GetProperty("tasks").EnumerateArray().Select(task => new PeriodFacts.TaskFact(
                    task.GetProperty("title").GetString()!,
                    task.GetProperty("moves").GetInt32()))],
            });
            var expected = testCase.GetProperty("expect").EnumerateArray()
                .Select(question => (question.GetProperty("prompt").GetString()!, question.GetProperty("subject").GetString()));
            Assert.Equal(expected, questions.Select(question => (question.PromptId, question.Subject)));
        }
    }

    [Fact]
    public void EveryPromptText()
    {
        foreach (var testCase in vectors.GetProperty("promptTexts").EnumerateArray())
        {
            var prompt = library[testCase.GetProperty("prompt").GetString()!]!;
            Assert.Equal(
                testCase.GetProperty("expect").GetString(),
                PromptRules.Text(prompt, testCase.GetProperty("kind").GetString()!, testCase.GetProperty("subject").GetString()));
        }
    }

    private static IEnumerable<string> Ids(JsonElement testCase) =>
        testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!);
}

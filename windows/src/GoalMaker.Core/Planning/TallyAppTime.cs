namespace GoalMaker.Core.Planning;

/// <summary>One app's minutes in a category (its executable in lower case), and its sites or folders.</summary>
public sealed record TallyAppTime(string App, int Minutes, IReadOnlyList<TallyWindowTime> Windows);

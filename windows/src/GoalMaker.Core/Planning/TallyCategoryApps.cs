namespace GoalMaker.Core.Planning;

/// <summary>One category's minutes on this device and the apps that made them up, most first.</summary>
public sealed record TallyCategoryApps(string Category, int Minutes, IReadOnlyList<TallyAppTime> Apps);

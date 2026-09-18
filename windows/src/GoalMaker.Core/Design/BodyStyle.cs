namespace GoalMaker.Core.Design;

/// <summary>The reading font: its regular weight, the weight for emphasis, and its width.</summary>
public sealed record BodyStyle(string Family, int Weight, int StrongWeight, double Width);

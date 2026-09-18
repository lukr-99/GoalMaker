namespace GoalMaker.Core.Design;

/// <summary>A heading or number style: which font, how heavy, how wide, and whether it slants or shouts.</summary>
public sealed record TypeStyle(string Family, int Weight, double Width, bool Italic, bool Uppercase, double Tracking);

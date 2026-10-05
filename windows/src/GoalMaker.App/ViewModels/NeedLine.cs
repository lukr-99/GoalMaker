namespace GoalMaker.App.ViewModels;

/// <summary>
/// The line under a need's title (docs/wants.md): what comes before the day it is needed by (the
/// price), the day itself, and what comes after (by Claude, bought or dropped), separators included.
/// </summary>
public sealed record NeedLine(string Before, string Due, string After, bool Late);

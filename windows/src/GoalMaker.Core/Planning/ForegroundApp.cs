namespace GoalMaker.Core.Planning;

/// <summary>The window in front on this PC: its executable's file name (code.exe) and its title.</summary>
public sealed record ForegroundApp(string App, string? Title);

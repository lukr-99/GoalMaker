namespace GoalMaker.Core.Settings;

/// <summary>
/// Where a mini window was last and whether it was pinned on top (spec, story 80). One per mini
/// window, kept by name so a window that goes away leaves nothing behind.
/// </summary>
public sealed record MiniWindowState(WindowPlacement Placement, bool Pinned);

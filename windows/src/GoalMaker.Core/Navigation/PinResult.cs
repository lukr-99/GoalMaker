namespace GoalMaker.Core.Navigation;

/// <summary>The pins after pinning or unpinning, and whether the change was refused (the pins are unchanged then).</summary>
public sealed record PinResult(IReadOnlyList<string> Pins, bool Refused);

namespace GoalMaker.Core.Design;

/// <summary>Animation durations in milliseconds (docs/design/spec.md, "Motion and feedback").</summary>
public sealed record MotionTokens(int Quick, int Standard, int Emphasized);

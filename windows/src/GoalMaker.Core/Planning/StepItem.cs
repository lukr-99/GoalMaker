namespace GoalMaker.Core.Planning;

/// <summary>One line of a task's checklist (spec, story 14). Steps are not tasks.</summary>
public sealed record StepItem(string Id, string TaskId, string Title, bool Done);

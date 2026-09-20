namespace GoalMaker.Core.Planning;

/// <summary>One of a project's milestones, like M0 to M6 (docs/projects.md).</summary>
public sealed record ProjectMilestone(string Id, string ProjectId, string Name, double Position = 0, bool Deleted = false);

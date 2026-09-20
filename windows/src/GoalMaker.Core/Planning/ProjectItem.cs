namespace GoalMaker.Core.Planning;

/// <summary>
/// A project as the screens and rules see it (docs/projects.md): where its code lives, which area it
/// belongs to, and whether it is active, paused or done.
/// </summary>
public sealed record ProjectItem(string Id, string Name)
{
    public string Description { get; init; } = string.Empty;

    public string? AreaId { get; init; }

    public string Status { get; init; } = ProjectRules.Active;

    public string? RepositoryUrl { get; init; }

    public string? LocalFolder { get; init; }

    public string Notes { get; init; } = string.Empty;

    public double Position { get; init; }

    public bool Deleted { get; init; }
}

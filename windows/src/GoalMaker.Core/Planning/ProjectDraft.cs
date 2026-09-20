namespace GoalMaker.Core.Planning;

/// <summary>What a new project or an edit says (docs/projects.md).</summary>
public sealed record ProjectDraft(string Name)
{
    public string Description { get; init; } = string.Empty;

    public string? AreaId { get; init; }

    public string Status { get; init; } = ProjectRules.Active;

    public string? RepositoryUrl { get; init; }

    public string? LocalFolder { get; init; }

    public string Notes { get; init; } = string.Empty;
}

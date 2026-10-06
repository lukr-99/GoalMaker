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

    /// <summary>The key its items read by, GM in GM-12; null when it has none (docs/projects.md, "Item ids").</summary>
    public string? ItemKey { get; init; }

    /// <summary>Days a done item stays on the board after it was finished; null keeps it until archived by hand.</summary>
    public int? ArchiveAfterDays { get; init; } = ProjectRules.DefaultArchiveAfterDays;
}

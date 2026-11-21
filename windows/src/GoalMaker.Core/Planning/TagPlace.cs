namespace GoalMaker.Core.Planning;

/// <summary>Where a tag belongs: one area or one project (docs/lists.md, "Tags"). Null stands for everything.</summary>
public sealed record TagPlace(string? AreaId, string? ProjectId)
{
    public static TagPlace Area(string id) => new(id, null);

    public static TagPlace Project(string id) => new(null, id);
}

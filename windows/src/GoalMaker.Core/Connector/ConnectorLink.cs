namespace GoalMaker.Core.Connector;

/// <summary>A connector link as the owner may see it: when it was made, last used and revoked, never its secret.</summary>
public sealed record ConnectorLink(string Id, DateTimeOffset CreatedAt, DateTimeOffset? LastUsedAt, DateTimeOffset? RevokedAt)
{
    public bool Active => RevokedAt is null;
}

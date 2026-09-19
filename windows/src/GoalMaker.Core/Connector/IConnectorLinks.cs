namespace GoalMaker.Core.Connector;

/// <summary>
/// The owner's Claude connector links (docs/connector.md, supabase/migrations/0007). Every call throws
/// RemoteUnavailableException when the server can't be reached.
/// </summary>
public interface IConnectorLinks
{
    /// <summary>Every link the owner made, newest first.</summary>
    Task<IReadOnlyList<ConnectorLink>> ListAsync(CancellationToken cancellationToken = default);

    /// <summary>Makes a new link, which kills the previous one, and returns its secret. The server keeps only a hash.</summary>
    Task<string> CreateAsync(CancellationToken cancellationToken = default);

    /// <summary>Kills every active link.</summary>
    Task RevokeAsync(CancellationToken cancellationToken = default);

    /// <summary>The URL to paste into Claude: the backend's connector function with the secret.</summary>
    static string Url(string backendUrl, string secret) => backendUrl.TrimEnd('/') + "/functions/v1/connector/" + secret;
}

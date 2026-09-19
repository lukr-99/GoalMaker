namespace GoalMaker.Core.Sync;

/// <summary>
/// The server refused a row or call for good (row security, a check); retrying the same thing won't help.
/// <see cref="Code"/> is the database's SQLSTATE when the server named one, so callers can tell refusals apart.
/// </summary>
public sealed class RemoteRejectedException(string message, string? code = null) : Exception(message)
{
    public string? Code { get; } = code;
}

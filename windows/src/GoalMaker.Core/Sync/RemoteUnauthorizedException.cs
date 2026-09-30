namespace GoalMaker.Core.Sync;

/// <summary>
/// The server did not take the session's token (HTTP 401). Often the token only expired and a fresh
/// session fixes it, so callers that don't renew sessions treat it as temporary, like any other
/// <see cref="RemoteUnavailableException"/>. Sync renews the session and, when the server refuses
/// that too, the session has ended (docs/sign-in.md).
/// </summary>
public sealed class RemoteUnauthorizedException(string message) : RemoteUnavailableException(message);

namespace GoalMaker.Core.Sync;

/// <summary>The server couldn't be reached or answered with a temporary error; try again later.</summary>
public sealed class RemoteUnavailableException(string message, Exception? inner = null) : Exception(message, inner);

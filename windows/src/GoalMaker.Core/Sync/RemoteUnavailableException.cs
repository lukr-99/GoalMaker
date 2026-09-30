namespace GoalMaker.Core.Sync;

/// <summary>The server couldn't be reached or answered with a temporary error; try again later.</summary>
public class RemoteUnavailableException(string message, Exception? inner = null) : Exception(message, inner);

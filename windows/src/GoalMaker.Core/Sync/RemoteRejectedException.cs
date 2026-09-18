namespace GoalMaker.Core.Sync;

/// <summary>The server refused a row for good (row security, a check); retrying the same row won't help.</summary>
public sealed class RemoteRejectedException(string message) : Exception(message);

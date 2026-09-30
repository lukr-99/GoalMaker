namespace GoalMaker.Core.Auth;

/// <summary>What asking the server for a fresh session came to.</summary>
public enum SessionRenewal
{
    /// <summary>The server gave a fresh session; the call can be tried again.</summary>
    Renewed,

    /// <summary>The server could not be reached; the session is kept and tried again later.</summary>
    Offline,

    /// <summary>The server refused: the session has ended and the device is signed out.</summary>
    Refused,
}

namespace GoalMaker.Core.Auth;

/// <summary>Whether someone is signed in. <see cref="Loading"/> lasts until the stored session is read.</summary>
public abstract record AuthSession
{
    private AuthSession()
    {
    }

    public sealed record Loading : AuthSession;

    /// <summary>
    /// Nobody is signed in. <paramref name="SessionEnded"/> is true when the server refused the session
    /// rather than the owner or the PC's week signing out, so the sign-in screen can say so.
    /// </summary>
    public sealed record SignedOut(bool SessionEnded = false) : AuthSession;

    public sealed record SignedIn(string UserId, string Email) : AuthSession;
}

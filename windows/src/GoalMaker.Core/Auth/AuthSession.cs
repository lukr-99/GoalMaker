namespace GoalMaker.Core.Auth;

/// <summary>Whether someone is signed in. <see cref="Loading"/> lasts until the stored session is read.</summary>
public abstract record AuthSession
{
    private AuthSession()
    {
    }

    public sealed record Loading : AuthSession;

    public sealed record SignedOut : AuthSession;

    public sealed record SignedIn(string UserId, string Email) : AuthSession;
}

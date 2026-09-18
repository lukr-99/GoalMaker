namespace GoalMaker.Core.Auth;

/// <summary>The outcome of a sign-in step, with failures the UI can explain in plain words.</summary>
public abstract record AuthResult
{
    private AuthResult()
    {
    }

    public sealed record Success : AuthResult;

    public sealed record WrongOrExpiredCode : AuthResult;

    public sealed record TooManyRequests : AuthResult;

    public sealed record Offline : AuthResult;

    public sealed record Failed(string Detail) : AuthResult;
}

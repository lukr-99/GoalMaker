namespace GoalMaker.Core.Assistant;

/// <summary>
/// Why the assistant gave no answer. The first five are the function's own error codes (the M7 plan,
/// "The call"); <see cref="Offline"/> and <see cref="SignedOut"/> are this device's side.
/// </summary>
public enum AssistantProblem
{
    /// <summary><c>unavailable</c> (503): the server has no model key, so chat isn't set up there.</summary>
    Unavailable,

    /// <summary><c>rate_limited</c> (429): GoalMaker's own limits on requests.</summary>
    RateLimited,

    /// <summary><c>provider_limit</c> (429): the model's free tier is used up for now.</summary>
    ProviderLimit,

    /// <summary><c>bad_request</c> (400): the server couldn't take the thread.</summary>
    BadRequest,

    /// <summary><c>failed</c> (502), or anything else that went wrong on the way.</summary>
    Failed,

    /// <summary>The server can't be reached.</summary>
    Offline,

    /// <summary>There is no session, or the server refused it.</summary>
    SignedOut,
}

namespace GoalMaker.Core.Auth;

/// <summary>
/// How long a PC keeps a session before the owner signs in again (docs/sign-in.md). The session is
/// kept and refreshed the whole time, so the app works offline all week; the week only decides when
/// the emailed code is asked for once more.
/// </summary>
public static class SignInPolicy
{
    /// <summary>How long a device is trusted after a sign-in.</summary>
    public static readonly TimeSpan Trusted = TimeSpan.FromDays(7);

    /// <summary>When a device that signed in at <paramref name="signedInAt"/> has to sign in again.</summary>
    public static DateTimeOffset DueAt(DateTimeOffset signedInAt) => Started(signedInAt, signedInAt) + Trusted;

    /// <summary>
    /// Whether the week has run out. A clock that jumped forwards and back again never locks the
    /// owner out early: a sign-in recorded in the future counts as this moment's.
    /// </summary>
    public static bool DueForSignIn(DateTimeOffset signedInAt, DateTimeOffset now) =>
        now >= Started(signedInAt, now) + Trusted;

    // A sign-in can't be later than the moment it is read at.
    private static DateTimeOffset Started(DateTimeOffset signedInAt, DateTimeOffset now) =>
        signedInAt > now ? now : signedInAt;
}

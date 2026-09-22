using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Auth;

/// <summary>
/// Holds this PC to its week (<see cref="SignInPolicy"/>): it writes down when the owner signed in,
/// and signs the device out at start-up once the week has run out, so the code is asked for again.
/// </summary>
public sealed class SignInWatch(IAuthGateway auth, ISettingsStore settings, Func<DateTimeOffset> now)
{
    /// <summary>Starts the week. Called after the code was accepted, not after a session was restored.</summary>
    public void RecordSignIn() => settings.SignedInAt = now();

    /// <summary>When this device signs in again, or null while nobody is signed in.</summary>
    public DateTimeOffset? DueAt() =>
        auth.Session is AuthSession.SignedIn && settings.SignedInAt is { } moment ? SignInPolicy.DueAt(moment) : null;

    /// <summary>
    /// Signs the device out when its week has run out. A session from before this policy has no
    /// sign-in written down; its week starts now rather than throwing the owner out on the update.
    /// </summary>
    public async Task EnforceAsync()
    {
        if (auth.Session is not AuthSession.SignedIn)
        {
            return;
        }

        if (settings.SignedInAt is not { } signedInAt)
        {
            settings.SignedInAt = now();
            return;
        }

        if (SignInPolicy.DueForSignIn(signedInAt, now()))
        {
            await auth.SignOutAsync().ConfigureAwait(false);
        }
    }
}

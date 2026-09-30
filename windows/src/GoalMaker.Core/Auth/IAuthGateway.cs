using GoalMaker.Core.Account;

namespace GoalMaker.Core.Auth;

/// <summary>Sign-in with an emailed 6-digit code. The Supabase adapter implements it; tests use a fake.</summary>
public interface IAuthGateway
{
    AuthSession Session { get; }

    event EventHandler<AuthSession>? SessionChanged;

    /// <summary>Restores a stored session, if any. Call once at start-up.</summary>
    Task InitializeAsync(CancellationToken cancellationToken);

    Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken);

    Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken);

    Task SignOutAsync();

    /// <summary>
    /// Asks the server for a fresh session after it refused a call as unauthorized. When it refuses
    /// that too, the device is signed out with <see cref="AuthSession.SignedOut.SessionEnded"/> and
    /// the replica is left alone, so the outbox waits for the same owner (docs/sign-in.md).
    /// </summary>
    Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken);

    /// <summary>Signs out because the server refuses even a fresh session. Like the week, it leaves the replica alone.</summary>
    Task EndSessionAsync();
}

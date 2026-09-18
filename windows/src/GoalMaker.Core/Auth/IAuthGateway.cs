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
}

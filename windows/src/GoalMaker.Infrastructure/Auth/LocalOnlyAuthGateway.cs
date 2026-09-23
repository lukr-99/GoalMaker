using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;

namespace GoalMaker.Infrastructure.Auth;

/// <summary>
/// A dev build's <see cref="IAuthGateway"/>: signed in, as soon as it starts, as one fixed owner who lives
/// only on this PC, so trying a change out never waits on a sign-in (docs/sign-in.md). Sign-in is for
/// release builds, and for a dev build started with <c>--sign-in</c>.
/// </summary>
public sealed class LocalOnlyAuthGateway : IAuthGateway
{
    /// <summary>The owner of every row a dev build writes; the same one the phone's dev build uses.</summary>
    public const string OwnerId = "00000000-0000-4000-8000-00000000d0e0";

    public AuthSession Session { get; private set; } = new AuthSession.Loading();

    public event EventHandler<AuthSession>? SessionChanged;

    public Task InitializeAsync(CancellationToken cancellationToken)
    {
        Session = new AuthSession.SignedIn(OwnerId, string.Empty);
        SessionChanged?.Invoke(this, Session);
        return Task.CompletedTask;
    }

    public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
        Task.FromResult<AuthResult>(new AuthResult.Success());

    public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
        Task.FromResult<AuthResult>(new AuthResult.Success());

    public Task SignOutAsync() => Task.CompletedTask;
}

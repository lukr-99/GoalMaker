using System.Net;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using Supabase.Gotrue;
using Supabase.Gotrue.Exceptions;
using Supabase.Gotrue.Interfaces;

namespace GoalMaker.Infrastructure.Auth;

/// <summary>
/// <see cref="IAuthGateway"/> over Supabase Auth's email one-time codes. The client signs out on its
/// own when the server refuses a refresh; any sign-out this gateway did not ask for is that, so it
/// reads as a session that ended (docs/sign-in.md).
/// </summary>
public sealed class SupabaseAuthGateway : IAuthGateway
{
    private readonly Supabase.Client client;
    private readonly IGotrueSessionPersistence<Session> storedSession;
    private AuthSession.SignedOut? leaving;

    public SupabaseAuthGateway(Supabase.Client client, IGotrueSessionPersistence<Session> storedSession)
    {
        this.client = client;
        this.storedSession = storedSession;
        client.Auth.AddStateChangedListener(OnAuthStateChanged);
    }

    public event EventHandler<AuthSession>? SessionChanged;

    public AuthSession Session { get; private set; } = new AuthSession.Loading();

    public async Task InitializeAsync(CancellationToken cancellationToken)
    {
        try
        {
            await client.InitializeAsync().ConfigureAwait(false);
        }
        catch (Exception error) when (error is GotrueException or HttpRequestException)
        {
            // Offline at start-up: a stored session stays usable and refreshes later.
        }

        Publish(FromCurrentUser());
    }

    public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
        AttemptAsync(() => client.Auth.SignInWithOtp(new SignInWithPasswordlessEmailOptions(email.Value)));

    public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
        AttemptAsync(async () =>
        {
            var session = await client.Auth.VerifyOTP(email.Value, code.Value, Constants.EmailOtpType.Email)
                .ConfigureAwait(false);
            if (session?.User is null)
            {
                throw new GotrueException("Token has expired or is invalid", FailureHint.Reason.UserBadLogin);
            }
        });

    public Task SignOutAsync() => LeaveAsync(new AuthSession.SignedOut());

    public Task EndSessionAsync() => LeaveAsync(new AuthSession.SignedOut(SessionEnded: true));

    public async Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken)
    {
        try
        {
            // This also reads the user back, so an account that is gone is refused here too.
            await client.Auth.RefreshSession().ConfigureAwait(false);
            return SessionRenewal.Renewed;
        }
        catch (GotrueException error) when (error.Reason is FailureHint.Reason.Offline or FailureHint.Reason.NetworkError
            or FailureHint.Reason.CloudflareNetworkError or FailureHint.Reason.UserTooManyRequests
            || error.StatusCode is 408 or >= 500)
        {
            return SessionRenewal.Offline;
        }
        catch (HttpRequestException)
        {
            return SessionRenewal.Offline;
        }
        catch (GotrueException)
        {
            await EndSessionAsync().ConfigureAwait(false);
            return SessionRenewal.Refused;
        }
    }

    private async Task LeaveAsync(AuthSession.SignedOut signedOut)
    {
        leaving = signedOut;
        try
        {
            await client.Auth.SignOut().ConfigureAwait(false);
        }
        catch (Exception error) when (error is GotrueException or HttpRequestException)
        {
            // Offline, or the server no longer knows the session. The client only forgets a session
            // the server let go of, so it is forgotten here; the server's token expires on its own.
            storedSession.DestroySession();
            client.Auth.LoadSession();
        }
        finally
        {
            leaving = null;
        }

        Publish(signedOut);
    }

    private void OnAuthStateChanged(IGotrueClient<User, Session> sender, Constants.AuthState state) =>
        Publish(state == Constants.AuthState.SignedOut ? leaving ?? new AuthSession.SignedOut(SessionEnded: true) : FromCurrentUser());

    // Nobody signed in keeps the reason it already has, so a session the server refused while the
    // stored one was being restored still says so.
    private AuthSession FromCurrentUser() =>
        client.Auth.CurrentUser is { } user
            ? new AuthSession.SignedIn(user.Id ?? string.Empty, user.Email ?? string.Empty)
            : Session as AuthSession.SignedOut ?? new AuthSession.SignedOut();

    private void Publish(AuthSession session)
    {
        if (session == Session)
        {
            return;
        }

        Session = session;
        SessionChanged?.Invoke(this, session);
    }

    private static async Task<AuthResult> AttemptAsync(Func<Task> action)
    {
        try
        {
            await action().ConfigureAwait(false);
            return new AuthResult.Success();
        }
        catch (GotrueException error)
        {
            return error.Reason switch
            {
                FailureHint.Reason.Offline or FailureHint.Reason.NetworkError => new AuthResult.Offline(),
                FailureHint.Reason.UserTooManyRequests => new AuthResult.TooManyRequests(),
                _ when error.StatusCode == (int)HttpStatusCode.TooManyRequests => new AuthResult.TooManyRequests(),
                _ when error.StatusCode == (int)HttpStatusCode.Forbidden
                    || error.Message.Contains("expired", StringComparison.OrdinalIgnoreCase)
                    || error.Message.Contains("invalid", StringComparison.OrdinalIgnoreCase) => new AuthResult.WrongOrExpiredCode(),
                _ => new AuthResult.Failed(error.Message),
            };
        }
        catch (HttpRequestException)
        {
            return new AuthResult.Offline();
        }
    }
}

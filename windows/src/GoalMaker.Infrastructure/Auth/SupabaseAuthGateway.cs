using System.Net;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using Supabase.Gotrue;
using Supabase.Gotrue.Exceptions;
using Supabase.Gotrue.Interfaces;

namespace GoalMaker.Infrastructure.Auth;

/// <summary><see cref="IAuthGateway"/> over Supabase Auth's email one-time codes.</summary>
public sealed class SupabaseAuthGateway : IAuthGateway
{
    private readonly Supabase.Client client;

    public SupabaseAuthGateway(Supabase.Client client)
    {
        this.client = client;
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

    public async Task SignOutAsync()
    {
        try
        {
            await client.Auth.SignOut().ConfigureAwait(false);
        }
        catch (Exception error) when (error is GotrueException or HttpRequestException)
        {
            // Offline: the stored session is removed anyway; the server token expires on its own.
        }

        Publish(new AuthSession.SignedOut());
    }

    private void OnAuthStateChanged(IGotrueClient<User, Session> sender, Constants.AuthState state) =>
        Publish(state == Constants.AuthState.SignedOut ? new AuthSession.SignedOut() : FromCurrentUser());

    private AuthSession FromCurrentUser() =>
        client.Auth.CurrentUser is { } user
            ? new AuthSession.SignedIn(user.Id ?? string.Empty, user.Email ?? string.Empty)
            : new AuthSession.SignedOut();

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

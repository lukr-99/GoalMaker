using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Sync;

/// <summary>
/// A session the server refuses while the app is open (M6-06, docs/sign-in.md): the owner is sent to
/// sign in, the outbox stays, and only the same owner gets it.
/// </summary>
public sealed class SessionEndTests : IDisposable
{
    private const string Other = "22222222-2222-2222-2222-222222222222";

    private readonly TestReplica test = new();
    private readonly FakeServer server = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private readonly FakeAuth auth = new();
    private readonly SyncCoordinator coordinator;
    private int runs;

    public SessionEndTests()
    {
        var engine = new SyncEngine(
            test.Catalog, test.Replica, server, time, owner: () => (auth.Session as AuthSession.SignedIn)?.UserId);
        coordinator = new SyncCoordinator(engine, test.Replica, time, TimeSpan.FromSeconds(2), auth);
        var sessionSync = new SessionSync(test.Catalog, test.Replica, coordinator);
        auth.SessionChanged += (_, session) => sessionSync.Apply(session);
        coordinator.StatusChanged += (_, status) =>
        {
            if (status.State == SyncState.Syncing)
            {
                Interlocked.Increment(ref runs);
            }
        };
    }

    private CancellationToken Token => TestContext.Current.CancellationToken;

    public void Dispose()
    {
        coordinator.Dispose();
        test.Dispose();
    }

    [Fact]
    public async Task ARefusedSessionSignsOutAndKeepsTheOutbox()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.RefusesSession = true;
        auth.Renewal = SessionRenewal.Refused;

        await coordinator.SyncNowAsync(Token);

        Assert.Equal(new AuthSession.SignedOut(SessionEnded: true), auth.Session);
        Assert.Single(test.Replica.Outbox());
        Assert.Single(test.Replica.All("tasks"));
        Assert.Empty(server.Rows("tasks"));

        // Nothing retries while nobody is signed in; the owner signing in again is what syncs.
        time.Advance(SyncCoordinator.LongestRetry);
        await Task.Delay(50, Token);
        Assert.Equal(1, runs);
    }

    [Fact]
    public async Task ASessionRefusedAgainAfterARenewalHasEnded()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.RefusesSession = true;

        await coordinator.SyncNowAsync(Token);

        Assert.Equal(1, auth.Renewals);
        Assert.Equal(new AuthSession.SignedOut(SessionEnded: true), auth.Session);
        Assert.Single(test.Replica.Outbox());
    }

    [Fact]
    public async Task AnExpiredTokenIsRenewedAndTheRunGoesOn()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.RefusesSession = true;
        auth.Renewed = () => server.RefusesSession = false;

        await coordinator.SyncNowAsync(Token);

        Assert.IsType<AuthSession.SignedIn>(auth.Session);
        Assert.Empty(test.Replica.Outbox());
        Assert.Single(server.Rows("tasks"));
    }

    [Fact]
    public async Task ARenewalThatCannotReachTheServerKeepsTheSession()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.RefusesSession = true;
        auth.Renewal = SessionRenewal.Offline;

        await coordinator.SyncNowAsync(Token);

        Assert.IsType<AuthSession.SignedIn>(auth.Session);
        Assert.Equal(SyncState.Offline, coordinator.Status.State);
        Assert.Single(test.Replica.Outbox());
    }

    [Fact]
    public async Task SigningBackInAsTheSameOwnerSyncsTheOutbox()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.RefusesSession = true;
        auth.Renewal = SessionRenewal.Refused;
        await coordinator.SyncNowAsync(Token);

        server.RefusesSession = false;
        auth.SignIn(TestReplica.Owner);
        time.Advance(TimeSpan.FromSeconds(2));
        await WaitUntil(() => coordinator.Status is { State: SyncState.Idle, PendingChanges: 0 });

        Assert.Equal("Run", (string?)Assert.Single(server.Rows("tasks"))["title"]);
        Assert.Single(test.Replica.All("tasks"));
    }

    [Fact]
    public async Task SigningInAsSomeoneElseNeverSendsTheLastOwnersOutbox()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.RefusesSession = true;
        auth.Renewal = SessionRenewal.Refused;
        await coordinator.SyncNowAsync(Token);

        server.RefusesSession = false;
        auth.SignIn(Other);
        Assert.Empty(test.Replica.Outbox());
        Assert.Empty(test.Replica.All("tasks"));

        time.Advance(TimeSpan.FromSeconds(2));
        await WaitUntil(() => coordinator.Status is { State: SyncState.Idle, LastSyncedAt: not null });
        Assert.Equal(0, server.Upserts);
    }

    [Fact]
    public async Task ARunOnlyPushesTheSignedInOwnersRows()
    {
        // A run that races the sign-in, before the replica was emptied, still leaves them alone.
        var owner = Other;
        var engine = new SyncEngine(test.Catalog, test.Replica, server, time, owner: () => owner);
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));

        var report = await engine.RunAsync(Token);

        Assert.Equal(0, report.Pushed);
        Assert.Equal(0, server.Upserts);
        Assert.Single(test.Replica.Outbox());

        owner = null;
        Assert.True((await engine.RunAsync(Token)).Offline);
        Assert.Equal(0, server.Upserts);
    }

    private static async Task WaitUntil(Func<bool> condition)
    {
        for (var attempt = 0; attempt < 200 && !condition(); attempt++)
        {
            await Task.Delay(10);
        }

        Assert.True(condition());
    }

    /// <summary>Signed in as the test owner. A refused renewal, or ending the session, signs out as ended.</summary>
    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged;

        public AuthSession Session { get; private set; } = new AuthSession.SignedIn(TestReplica.Owner, "me@example.com");

        public SessionRenewal Renewal { get; set; } = SessionRenewal.Renewed;

        public Action? Renewed { get; set; }

        public int Renewals { get; private set; }

        public void SignIn(string userId) => Publish(new AuthSession.SignedIn(userId, "someone@example.com"));

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task SignOutAsync()
        {
            Publish(new AuthSession.SignedOut());
            return Task.CompletedTask;
        }

        public Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken)
        {
            Renewals++;
            if (Renewal == SessionRenewal.Refused)
            {
                Publish(new AuthSession.SignedOut(SessionEnded: true));
            }
            else if (Renewal == SessionRenewal.Renewed)
            {
                Renewed?.Invoke();
            }

            return Task.FromResult(Renewal);
        }

        public Task EndSessionAsync()
        {
            Publish(new AuthSession.SignedOut(SessionEnded: true));
            return Task.CompletedTask;
        }

        private void Publish(AuthSession session)
        {
            Session = session;
            SessionChanged?.Invoke(this, session);
        }
    }
}

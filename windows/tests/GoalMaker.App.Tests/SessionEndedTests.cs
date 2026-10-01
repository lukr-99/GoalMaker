using System.Text.Json.Nodes;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Problems;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>
/// A session the server refuses while the app is open (M6-06, docs/sign-in.md): the window goes to
/// sign-in and says why, the outbox stays, and it syncs when the same owner signs in again.
/// </summary>
public sealed class SessionEndedTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly Remote remote = new();
    private readonly FakeAuth auth = new();
    private readonly SyncCoordinator sync;
    private readonly ShellViewModel shell;

    public SessionEndedTests()
    {
        var catalog = ContractResources.SyncedTables();
        var engine = new SyncEngine(
            catalog, planner.Replica, remote, planner.Time, owner: () => (auth.Session as AuthSession.SignedIn)?.UserId);
        sync = new SyncCoordinator(engine, planner.Replica, planner.Time, TimeSpan.FromSeconds(2), auth);
        var sessionSync = new SessionSync(catalog, planner.Replica, sync);
        auth.SessionChanged += (_, session) => sessionSync.Apply(session);
        var signIn = new SignInViewModel(auth, new SignInWatch(auth, planner.Settings, () => planner.Time.GetUtcNow()), planner.Strings, devBackend: null);
        shell = new ShellViewModel(auth, signIn, new ProblemLog(planner.Time), new TestUpdates().Service, action => action());
    }

    private CancellationToken Token => TestContext.Current.CancellationToken;

    public void Dispose()
    {
        sync.Dispose();
        planner.Dispose();
    }

    [Fact]
    public async Task ARefusedSessionGoesToSignInAndKeepsTheOutbox()
    {
        planner.Tasks.Add("Call the dentist");
        remote.RefusesSession = true;

        await sync.SyncNowAsync(Token);

        Assert.True(shell.IsSignedOut);
        Assert.True(shell.SignIn.SessionEnded);
        Assert.True(shell.SignIn.IsEmailStep);
        Assert.Equal("me@example.com", shell.SignIn.Email);
        Assert.Equal(1, planner.Replica.PendingCount());
        Assert.Equal("Call the dentist", planner.Task("Call the dentist").Title);
    }

    [Fact]
    public async Task SigningBackInAsTheSameOwnerSendsWhatWaited()
    {
        planner.Tasks.Add("Call the dentist");
        remote.RefusesSession = true;
        await sync.SyncNowAsync(Token);

        remote.RefusesSession = false;
        auth.SignIn(TestPlanner.Owner);
        Assert.True(shell.IsSignedIn);
        Assert.False(shell.SignIn.SessionEnded);
        await sync.SyncNowAsync(Token);

        Assert.Equal(0, planner.Replica.PendingCount());
        Assert.Equal("Call the dentist", (string?)Assert.Single(remote.Pushed)["title"]);
    }

    [Fact]
    public async Task SomeoneElseSigningInDoesNotGetTheWaitingChanges()
    {
        planner.Tasks.Add("Call the dentist");
        remote.RefusesSession = true;
        await sync.SyncNowAsync(Token);

        remote.RefusesSession = false;
        auth.SignIn("22222222-2222-2222-2222-222222222222");
        await sync.SyncNowAsync(Token);

        Assert.Empty(remote.Pushed);
        Assert.Equal(0, planner.Replica.PendingCount());
        Assert.Empty(planner.Tasks.All());
    }

    [Fact]
    public async Task SigningOutOnPurposeSaysNothingAboutASession()
    {
        await auth.SignOutAsync();

        Assert.True(shell.IsSignedOut);
        Assert.False(shell.SignIn.SessionEnded);
    }

    /// <summary>A server that keeps what it is sent, or refuses the session outright.</summary>
    private sealed class Remote : IRemoteTables
    {
        public bool RefusesSession { get; set; }

        public List<JsonObject> Pushed { get; } = [];

        public Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken)
        {
            Refuse();
            var stored = (JsonObject)row.DeepClone();
            stored["updated_at"] = "2026-09-18T14:00:00.000000Z";
            Pushed.Add(stored);
            return Task.FromResult((JsonObject)stored.DeepClone());
        }

        public Task<IReadOnlyList<JsonObject>> PullAsync(
            string table, string? from, RowCursor? after, int limit, CancellationToken cancellationToken)
        {
            Refuse();
            return Task.FromResult<IReadOnlyList<JsonObject>>([]);
        }

        private void Refuse()
        {
            if (RefusesSession)
            {
                throw new RemoteUnauthorizedException("HTTP 401: JWT expired");
            }
        }
    }

    /// <summary>Signed in as the planner's owner; the server refuses every renewal.</summary>
    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged;

        public AuthSession Session { get; private set; } = new AuthSession.SignedIn(TestPlanner.Owner, "me@example.com");

        public void SignIn(string userId) => Publish(new AuthSession.SignedIn(userId, "me@example.com"));

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
            Publish(new AuthSession.SignedOut(SessionEnded: true));
            return Task.FromResult(SessionRenewal.Refused);
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

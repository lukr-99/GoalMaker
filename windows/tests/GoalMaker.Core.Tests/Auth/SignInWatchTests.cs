using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Tests.Auth;

/// <summary>The week a PC keeps its session for before the owner signs in again (docs/sign-in.md).</summary>
public sealed class SignInWatchTests
{
    private static readonly DateTimeOffset Monday = new(2026, 9, 21, 9, 0, 0, TimeSpan.Zero);

    [Fact]
    public void TheWeekRunsOutSevenDaysAfterSigningIn()
    {
        Assert.Equal(Monday.AddDays(7), SignInPolicy.DueAt(Monday));
        Assert.False(SignInPolicy.DueForSignIn(Monday, Monday.AddDays(6).AddHours(23)));
        Assert.True(SignInPolicy.DueForSignIn(Monday, Monday.AddDays(7)));
        Assert.True(SignInPolicy.DueForSignIn(Monday, Monday.AddDays(30)));
    }

    [Fact]
    public void AClockThatRanAheadDoesNotLockTheOwnerOut()
    {
        // The sign-in was written down while the clock was a year fast; reading it back is not a week.
        Assert.False(SignInPolicy.DueForSignIn(Monday.AddYears(1), Monday));
    }

    [Fact]
    public async Task ASessionInsideItsWeekIsLeftAlone()
    {
        var auth = new FakeAuth();
        var settings = new FakeSettings { SignedInAt = Monday };
        var watch = new SignInWatch(auth, settings, () => Monday.AddDays(3));

        await watch.EnforceAsync();

        Assert.False(auth.SignedOut);
        Assert.Equal(Monday.AddDays(7), watch.DueAt());
    }

    [Fact]
    public async Task ASessionPastItsWeekIsSignedOut()
    {
        var auth = new FakeAuth();
        var settings = new FakeSettings { SignedInAt = Monday };
        var watch = new SignInWatch(auth, settings, () => Monday.AddDays(8));

        await watch.EnforceAsync();

        Assert.True(auth.SignedOut);
    }

    [Fact]
    public async Task ASessionFromBeforeThePolicyStartsItsWeekRatherThanBeingThrownOut()
    {
        var auth = new FakeAuth();
        var settings = new FakeSettings();
        var watch = new SignInWatch(auth, settings, () => Monday);

        await watch.EnforceAsync();

        Assert.False(auth.SignedOut);
        Assert.Equal(Monday, settings.SignedInAt);
    }

    [Fact]
    public async Task NobodySignedInNeedsNothing()
    {
        var auth = new FakeAuth { Session = new AuthSession.SignedOut() };
        var settings = new FakeSettings();
        var watch = new SignInWatch(auth, settings, () => Monday);

        await watch.EnforceAsync();

        Assert.False(auth.SignedOut);
        Assert.Null(settings.SignedInAt);
        Assert.Null(watch.DueAt());
    }

    [Fact]
    public void SigningInStartsTheWeekAgain()
    {
        var settings = new FakeSettings { SignedInAt = Monday };
        var watch = new SignInWatch(new FakeAuth(), settings, () => Monday.AddDays(9));

        watch.RecordSignIn();

        Assert.Equal(Monday.AddDays(9), settings.SignedInAt);
    }

    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged
        {
            add { }
            remove { }
        }

        public AuthSession Session { get; set; } = new AuthSession.SignedIn("owner", "me@example.com");

        public bool SignedOut { get; private set; }

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task SignOutAsync()
        {
            SignedOut = true;
            Session = new AuthSession.SignedOut();
            return Task.CompletedTask;
        }
    }

    private sealed class FakeSettings : ISettingsStore
    {
        public Appearance Appearance { get; set; } = Appearance.Default;

        public int DayStartHour { get; set; } = PlanningDay.DefaultStartHour;

        public QuietHours QuietHours { get; set; } = QuietHours.Off;

        public TimeOnly? PlanTomorrowReminder { get; set; }

        public TimeOnly? WeeklyReviewReminder { get; set; }

        public int WeeklyReviewWeekday { get; set; } = 1;

        public TimeOnly? MonthlyReviewReminder { get; set; }

        public DateTimeOffset? RemindedUntil { get; set; }

        public DateTimeOffset? SignedInAt { get; set; }

        public string? QuickAddHotkey { get; set; }

        public bool NavigationCollapsed { get; set; }

        public BackendEnvironment? BackendOverride { get; set; }

        public WindowPlacement? MainWindowPlacement { get; set; }

        public IReadOnlyDictionary<string, MiniWindowState> MiniWindows { get; set; } =
            new Dictionary<string, MiniWindowState>(StringComparer.Ordinal);

        public string? WeeklyBackupFolder { get; set; }

        public DateTimeOffset? WeeklyBackupWritten { get; set; }
    }
}

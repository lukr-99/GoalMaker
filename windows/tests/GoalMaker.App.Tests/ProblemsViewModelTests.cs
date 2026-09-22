using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Problems;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.App.Tests;

/// <summary>
/// Where a problem shows (M6-08, docs/problems.md): in Settings, with a quiet mark on the item, and
/// gone again when it comes right by itself.
/// </summary>
public sealed class ProblemsViewModelTests
{
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 21, 10, 0, 0, TimeSpan.Zero));

    [Fact]
    public void APushThatWouldNotGoShowsInSettingsAndMarksTheItem()
    {
        var log = new ProblemLog(time);
        var page = new ProblemsViewModel(log, new KeyStrings(), action => action());
        var shell = Shell(log);
        Assert.False(page.HasProblems);
        Assert.False(shell.HasProblems);

        log.Report(ProblemRules.Sync, "23502: null value in column \"priority\"");

        Assert.True(shell.HasProblems);
        var row = Assert.Single(page.Rows);
        Assert.Equal(("Problems.Sync", "Problems.SyncAdvice"), (row.Title, row.Advice));
        Assert.Equal("23502: null value in column \"priority\"", row.Detail);
        Assert.True(row.HasDetail);
        Assert.True(row.IsUnread);
    }

    [Fact]
    public void OpeningSettingsTakesTheMarkOffButLeavesTheProblem()
    {
        var log = new ProblemLog(time);
        var page = new ProblemsViewModel(log, new KeyStrings(), action => action());
        var shell = Shell(log);
        log.Report(ProblemRules.Sync, "the push that would not go");

        page.Read();

        Assert.False(shell.HasProblems);
        Assert.False(Assert.Single(page.Rows).IsUnread);
    }

    [Fact]
    public void ASyncThatWorksLaterClearsItWithNobodyDoingAnything()
    {
        var log = new ProblemLog(time);
        var page = new ProblemsViewModel(log, new KeyStrings(), action => action());
        var shell = Shell(log);
        log.Report(ProblemRules.Sync, "the push that would not go");

        log.Clear(ProblemRules.Sync);

        Assert.False(shell.HasProblems);
        Assert.False(page.HasProblems);
        Assert.Empty(page.Rows);
    }

    [Fact]
    public void AProblemWithNothingBehindItShowsNoWhatHappened()
    {
        var log = new ProblemLog(time);
        var page = new ProblemsViewModel(log, new KeyStrings(), action => action());

        log.Report(ProblemRules.Update, null);

        Assert.False(Assert.Single(page.Rows).HasDetail);
    }

    private static ShellViewModel Shell(ProblemLog log)
    {
        var auth = new SignedOutAuth();
        var watch = new SignInWatch(auth, new TestPlanner.FakeSettings(), () => DateTimeOffset.Now);
        return new ShellViewModel(auth, new SignInViewModel(auth, watch, new KeyStrings(), devBackend: null), log, action => action());
    }

    private sealed class KeyStrings : IStrings
    {
        public string Get(string key, params object[] arguments) => key;
    }

    private sealed class SignedOutAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged
        {
            add { }
            remove { }
        }

        public AuthSession Session => new AuthSession.SignedOut();

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task SignOutAsync() => Task.CompletedTask;
    }
}

using System.IO;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Notices;
using GoalMaker.Core.Problems;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.App.Tests;

/// <summary>
/// What the owner sees (docs/problems.md): which icon sits on the Settings item and what its tooltip
/// says, which lines the Settings banner shows, and when each goes.
/// </summary>
public sealed class NoticesTests
{
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 10, 18, 10, 0, 0, TimeSpan.Zero));
    private readonly TestUpdates updates = new();
    private readonly ProblemLog problems;
    private readonly ShellViewModel shell;
    private readonly SettingsNoticesViewModel banner;

    public NoticesTests()
    {
        problems = new ProblemLog(time);
        var board = new NoticeBoard(problems, updates.Service);
        var auth = new SignedOutAuth();
        var watch = new SignInWatch(auth, new TestPlanner.FakeSettings(), () => DateTimeOffset.Now);
        shell = new ShellViewModel(auth, new SignInViewModel(auth, watch, new KeyStrings(), devBackend: null), board, new KeyStrings(), action => action());
        banner = new SettingsNoticesViewModel(board, new KeyStrings(), action => action());
    }

    private static CancellationToken Token => TestContext.Current.CancellationToken;

    [Fact]
    public void NothingToActOnShowsNoIconAndNoBanner()
    {
        Assert.False(shell.HasSettingsMark);
        Assert.Equal(string.Empty, shell.SettingsMarkText);
        Assert.False(banner.HasNotices);
        Assert.Empty(banner.Rows);
    }

    [Fact]
    public void ASyncProblemShowsTheWarningIconAndALineThatLeadsToTheProblems()
    {
        problems.Report(ProblemRules.Sync, "the push that would not go");

        Assert.True(shell.HasProblems);
        Assert.False(shell.HasUpdate);
        Assert.Equal("Nav.Mark.Sync", shell.SettingsMarkText);
        var row = Assert.Single(banner.Rows);
        Assert.Equal(("Notice.Sync", "Notice.SyncAdvice", "Notice.SyncAction"), (row.Title, row.Advice, row.ActionText));
        Assert.False(row.IsUpdate);
        Assert.Equal("problems", NoticeCopy.Section(row.Notice));
    }

    [Fact]
    public async Task AnUpdateShowsTheDownloadIconAndALineThatInstallsIt()
    {
        await updates.Service.CheckAsync(Token);

        Assert.True(shell.HasUpdate);
        Assert.False(shell.HasProblems);
        Assert.Equal("Nav.Mark.UpdateReady", shell.SettingsMarkText);
        var row = Assert.Single(banner.Rows);
        Assert.True(row.IsUpdate);
        Assert.Equal(new Notice.UpdateReady("1.1.0"), row.Notice);
        Assert.Equal(("Notice.UpdateReady", "Notice.Install"), (row.Title, row.ActionText));
        Assert.Null(NoticeCopy.Section(row.Notice));
    }

    [Fact]
    public async Task AnUpdateAndAProblemShowBothIconsAndTheUpdateComesFirstInTheBanner()
    {
        problems.Report(ProblemRules.Backup, "the folder is gone");
        await updates.Service.CheckAsync(Token);

        Assert.True(shell.HasProblems);
        Assert.True(shell.HasUpdate);
        Assert.Equal("Nav.Mark.Several", shell.SettingsMarkText);
        Assert.Collection(
            banner.Rows,
            row => Assert.True(row.IsUpdate),
            row => Assert.Equal("Notice.Backup", row.Title));
    }

    [Fact]
    public void OpeningSettingsTakesTheWarningOffTheItemButTheBannerStillSaysWhatIsWrong()
    {
        problems.Report(ProblemRules.Sync, "the push that would not go");

        problems.Read();

        Assert.False(shell.HasSettingsMark);
        Assert.Equal(string.Empty, shell.SettingsMarkText);
        Assert.Equal("Notice.Sync", Assert.Single(banner.Rows).Title);
    }

    [Fact]
    public void AProblemThatComesRightLeavesTheItemAndTheBanner()
    {
        problems.Report(ProblemRules.Sync, "the push that would not go");

        problems.Clear(ProblemRules.Sync);

        Assert.False(shell.HasSettingsMark);
        Assert.False(banner.HasNotices);
    }

    [Fact]
    public void DismissingAProblemHidesItUntilItComesRightAndGoesWrongAgain()
    {
        problems.Report(ProblemRules.Sync, "the push that would not go");

        Assert.Single(banner.Rows).DismissCommand.Execute(null);

        Assert.False(banner.HasNotices);
        Assert.False(shell.HasSettingsMark);
        Assert.Single(problems.Problems);

        time.Advance(TimeSpan.FromMinutes(5));
        problems.Report(ProblemRules.Sync, "the push that would not go");
        Assert.False(banner.HasNotices);

        problems.Clear(ProblemRules.Sync);
        time.Advance(TimeSpan.FromMinutes(5));
        problems.Report(ProblemRules.Sync, "the push that would not go");
        Assert.True(banner.HasNotices);
        Assert.True(shell.HasProblems);
    }

    [Fact]
    public async Task DismissingAnUpdateHidesItUntilANewerVersionComes()
    {
        await updates.Service.CheckAsync(Token);

        Assert.Single(banner.Rows).DismissCommand.Execute(null);

        Assert.False(banner.HasNotices);
        Assert.False(shell.HasUpdate);

        updates.Latest = "1.2.0";
        await updates.Service.CheckAsync(Token);
        Assert.Equal(new Notice.UpdateReady("1.2.0"), Assert.Single(banner.Rows).Notice);
        Assert.True(shell.HasUpdate);
    }

    [Fact]
    public async Task ACheckThatFindsNothingNewTakesTheUpdateAway()
    {
        await updates.Service.CheckAsync(Token);

        updates.Latest = "1.0.0";
        await updates.Service.CheckAsync(Token);

        Assert.False(shell.HasUpdate);
        Assert.False(banner.HasNotices);
    }

    [Fact]
    public void TheButtonAsksThePageToActOnThatNotice()
    {
        problems.Report(ProblemRules.Update, null);
        Notice? asked = null;
        banner.ActRequested += (_, notice) => asked = notice;

        Assert.Single(banner.Rows).ActCommand.Execute(null);

        Assert.Equal("updates", NoticeCopy.Section(Assert.IsType<Notice.ProblemWaiting>(asked)));
    }

    [Fact]
    public void EachProblemKindLeadsToTheSectionThatHelps()
    {
        Assert.Equal("problems", NoticeCopy.Section(Of(ProblemRules.Sync)));
        Assert.Equal("data", NoticeCopy.Section(Of(ProblemRules.Backup)));
        Assert.Equal("updates", NoticeCopy.Section(Of(ProblemRules.Update)));
    }

    [Fact]
    public void EveryNoticeKeyIsInTheStrings()
    {
        var strings = new RecordingStrings();
        Notice[] all = [new Notice.UpdateReady("1.1.0"), .. ProblemRules.Kinds.Select(Of)];
        foreach (var notice in all)
        {
            _ = new SettingsNoticeRowViewModel(notice, strings, _ => { }, _ => { });
            NoticeCopy.Mark([notice], strings);
        }

        NoticeCopy.Mark(all, strings);

        var file = File.ReadAllText(Path.Combine(RepositoryRoot(), "windows", "src", "GoalMaker.App", "Resources", "Strings.xaml"));
        Assert.All(strings.Keys, key => Assert.Contains($"x:Key=\"{key}\"", file, StringComparison.Ordinal));
    }

    private Notice.ProblemWaiting Of(string kind) => new(new Problem(kind, time.GetUtcNow(), null));

    private static string RepositoryRoot()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "contracts")))
        {
            directory = directory.Parent;
        }

        return directory?.FullName ?? throw new DirectoryNotFoundException("contracts/ not found above " + AppContext.BaseDirectory);
    }

    private sealed class KeyStrings : IStrings
    {
        public string Get(string key, params object[] arguments) => key;
    }

    private sealed class RecordingStrings : IStrings
    {
        public HashSet<string> Keys { get; } = [];

        public string Get(string key, params object[] arguments)
        {
            Keys.Add(key);
            return key;
        }
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

        public Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken) => Task.FromResult(SessionRenewal.Renewed);

        public Task EndSessionAsync() => Task.CompletedTask;
    }
}

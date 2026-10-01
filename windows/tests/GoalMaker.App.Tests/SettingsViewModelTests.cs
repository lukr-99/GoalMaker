using System.Net.Http;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Backup;
using GoalMaker.Core.Problems;
using GoalMaker.Core.Startup;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Sync;
using GoalMaker.Infrastructure.Updates;

namespace GoalMaker.App.Tests;

/// <summary>
/// The updates card: the owner can always download a release themselves (ADR 0010), and an update
/// the check found marks the Settings item and stands out in the card until it is installed.
/// </summary>
public sealed class SettingsViewModelTests : IDisposable
{
    private const string ReleasesPage = "https://github.com/lukr-99/GoalMaker/releases/latest";
    private readonly TestPlanner planner = new();
    private readonly List<string> opened = [];

    [Fact]
    public async Task AfterAFailedCheckTheReleasesPageOpensInTheBrowser()
    {
        var settings = Settings(ReleasesPage);

        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.StartsWith("Settings.Update.Failed", settings.UpdateStatus, StringComparison.Ordinal);
        Assert.True(settings.HasReleasesPage);
        Assert.True(settings.OpenReleasesPageCommand.CanExecute(null));
        settings.OpenReleasesPageCommand.Execute(null);
        Assert.Equal([ReleasesPage], opened);
    }

    [Fact]
    public void WithoutAChannelThereIsNoPageToOpen()
    {
        var settings = Settings(releasesPage: null);

        Assert.False(settings.HasReleasesPage);
        Assert.False(settings.OpenReleasesPageCommand.CanExecute(null));
        Assert.Empty(opened);
    }

    [Fact]
    public async Task AFoundUpdateMarksSettingsAndStandsOutInTheCard()
    {
        var channel = new TestUpdates();
        var shell = Shell(channel.Service);
        var settings = Settings(ReleasesPage, channel.Service);
        Assert.False(shell.HasUpdate);
        Assert.False(shell.HasSettingsMark);
        Assert.False(settings.CanInstall);

        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.True(shell.HasUpdate);
        Assert.True(shell.HasSettingsMark);
        Assert.True(settings.CanInstall);
        Assert.Equal("Settings.Update.Available(1.1.0)", settings.AvailableText);
        Assert.Equal("Settings.InstallUpdate(1.1.0)", settings.InstallText);
        // The accent row says it once; the status line stays empty.
        Assert.False(settings.HasUpdateStatus);
    }

    [Fact]
    public async Task TheMarkGoesWhenACheckFindsNoUpdate()
    {
        var channel = new TestUpdates();
        var shell = Shell(channel.Service);
        var settings = Settings(ReleasesPage, channel.Service);
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        channel.Reachable = false;
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.False(shell.HasUpdate);
        Assert.False(shell.HasSettingsMark);
        Assert.False(settings.CanInstall);
        Assert.Equal(string.Empty, settings.AvailableText);
    }

    [Fact]
    public async Task NoMarkWhenTheLatestIsInstalled()
    {
        var channel = new TestUpdates(installed: "1.1.0");
        var shell = Shell(channel.Service);
        var settings = Settings(ReleasesPage, channel.Service);

        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.False(shell.HasUpdate);
        Assert.False(settings.CanInstall);
        Assert.StartsWith("Settings.Update.UpToDate", settings.UpdateStatus, StringComparison.Ordinal);
    }

    [Fact]
    public async Task AFailedInstallKeepsTheUpdateToTryAgain()
    {
        var channel = new TestUpdates { Corrupt = true };
        var shell = Shell(channel.Service);
        var settings = Settings(ReleasesPage, channel.Service);
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        await settings.InstallUpdateCommand.ExecuteAsync(null);

        Assert.Equal("Settings.Update.Corrupted", settings.UpdateStatus);
        Assert.True(settings.CanInstall);
        Assert.True(shell.HasUpdate);
        Assert.Empty(channel.Launched);
    }

    [Fact]
    public async Task AnUpdateShowsOverAProblemAndBothCountAsAMark()
    {
        var channel = new TestUpdates();
        var log = new ProblemLog(planner.Time);
        var shell = Shell(channel.Service, log);
        log.Report(ProblemRules.Sync, null);
        Assert.True(shell.HasSettingsMark);
        Assert.False(shell.HasUpdate);

        await channel.Service.CheckAsync(TestContext.Current.CancellationToken);

        Assert.True(shell.HasProblems);
        Assert.True(shell.HasUpdate);
        Assert.True(shell.HasSettingsMark);
    }

    public void Dispose() => planner.Dispose();

    private ShellViewModel Shell(UpdateService updates, ProblemLog? problems = null)
    {
        var auth = new SignedOutAuth();
        var watch = new SignInWatch(auth, planner.Settings, () => DateTimeOffset.Now);
        return new ShellViewModel(
            auth, new SignInViewModel(auth, watch, planner.Strings, devBackend: null), problems ?? new ProblemLog(planner.Time), updates, action => action());
    }

    private SettingsViewModel Settings(string? releasesPage, UpdateService? service = null) =>
        Build(planner, planner.Strings, service, releasesPage, opened.Add);

    /// <summary>A Settings view model over the test planner, with nothing on the PC behind it.</summary>
    internal static SettingsViewModel Build(
        TestPlanner planner, GoalMaker.App.Localization.IStrings strings, UpdateService? service, string? releasesPage, Action<string> open)
    {
        var backend = new BackendEnvironment("http://127.0.0.1:55321", "key");
        var updates = service ?? new UpdateService(
            "1.0.0",
            ReleasePlatform.Windows,
            channelConfigured: true,
            new OfflineChannel(),
            new ReleaseVerifier(new NotConfiguredSignatureVerifier()),
            new NoInstaller());
        var backup = new BackupService(
            ContractResources.SyncedTables(), planner.Replica, () => TestPlanner.Owner, "1.0.0", "windows", planner.Time);
        var folder = new NoFolder();
        return new SettingsViewModel(
            new SignedOutAuth(),
            planner.Sync,
            planner.Settings,
            updates,
            new AppInfo("1.0.0", false, backend, backend),
            strings,
            ContractResources.Themes(),
            () => false,
            _ => { },
            () => { },
            () => { },
            _ => true,
            _ => { },
            backup,
            new WeeklyBackup(backup, planner.Settings, planner.Time, folder),
            folder,
            () => { },
            () => null,
            () => null,
            () => null,
            new NoSignInStartup(),
            new NoStartupProfiles(),
            new StartupProfilesRequest("com.goalmaker.app", "GoalMaker", "GoalMaker.exe"),
            () => { },
            releasesPage,
            open,
            action => action());
    }

    private sealed class OfflineChannel : IReleaseChannel
    {
        public Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken) =>
            throw new HttpRequestException("offline");

        public Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken) =>
            throw new HttpRequestException("offline");
    }

    private sealed class NoInstaller : IUpdateInstaller
    {
        public void Launch(string localPath)
        {
        }
    }

    private sealed class NoFolder : IBackupFolder
    {
        public bool Exists(string folder) => false;

        public bool Write(string folder, string name, string text) => false;

        public IReadOnlyList<string> Exports(string folder) => [];

        public void Remove(string folder, string name)
        {
        }
    }

    private sealed class NoSignInStartup : ISignInStartup
    {
        public bool IsOn => false;

        public bool Set(bool on) => false;
    }

    private sealed class NoStartupProfiles : IStartupProfiles
    {
        public string? Find() => null;

        public bool Ask(StartupProfilesRequest request) => false;
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

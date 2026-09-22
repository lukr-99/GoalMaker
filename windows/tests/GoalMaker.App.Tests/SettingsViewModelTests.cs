using System.Net.Http;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Backup;
using GoalMaker.Core.Startup;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Sync;
using GoalMaker.Infrastructure.Updates;

namespace GoalMaker.App.Tests;

/// <summary>The updates card: the owner can always download a release themselves (ADR 0010).</summary>
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

    public void Dispose() => planner.Dispose();

    private SettingsViewModel Settings(string? releasesPage)
    {
        var backend = new BackendEnvironment("http://127.0.0.1:55321", "key");
        var updates = new UpdateService(
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
            planner.Strings,
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
            opened.Add,
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
    }
}

using System.IO;
using System.Net.Http;
using System.Xml.Linq;
using DotNetLib.Tray;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Backup;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Problems;
using GoalMaker.Core.Settings;
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
        // The accent row says it once; the Check row keeps its hint.
        Assert.Null(settings.UpdateResult);
    }

    [Fact]
    public async Task TheMarkGoesWhenACheckFindsNoUpdate()
    {
        var channel = new TestUpdates();
        var shell = Shell(channel.Service);
        var settings = Settings(ReleasesPage, channel.Service);
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        channel.Latest = "1.0.0";
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.False(shell.HasUpdate);
        Assert.False(shell.HasSettingsMark);
        Assert.False(settings.CanInstall);
        Assert.Equal(string.Empty, settings.AvailableText);
    }

    [Fact]
    public async Task AFailedCheckSaysWhyAndKeepsTheMark()
    {
        var channel = new TestUpdates();
        var shell = Shell(channel.Service);
        var settings = Settings(ReleasesPage, channel.Service);
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        channel.Reachable = false;
        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.StartsWith("Settings.Update.Failed", settings.UpdateStatus, StringComparison.Ordinal);
        Assert.Equal(SettingsRowResult.Error, settings.UpdateStatusKind);
        Assert.True(shell.HasUpdate);
        Assert.True(settings.CanInstall);
        Assert.Equal("Settings.Update.Available(1.1.0)", settings.AvailableText);
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

    [Fact]
    public async Task AnUpdateTheQuietCheckFoundStandsOutInTheCard()
    {
        var channel = new TestUpdates();
        var settings = Settings(ReleasesPage, channel.Service);
        Assert.False(settings.CanInstall);

        // The daily check runs outside the page, through the same service.
        await channel.Service.CheckAsync(TestContext.Current.CancellationToken);

        Assert.True(settings.CanInstall);
        Assert.Equal("Settings.Update.Available(1.1.0)", settings.AvailableText);
        Assert.Empty(channel.Launched);
    }

    [Fact]
    public async Task TheCardSaysWhenACheckLastGotThrough()
    {
        var channel = new TestUpdates(installed: "1.1.0");
        var settings = Settings(ReleasesPage, channel.Service);
        Assert.False(settings.HasLastChecked);

        await settings.CheckForUpdatesCommand.ExecuteAsync(null);

        Assert.True(settings.HasLastChecked);
        Assert.StartsWith("Settings.Update.LastChecked", settings.LastCheckedText, StringComparison.Ordinal);
        Assert.Equal(settings.LastCheckedText, settings.UpdateCheckHint);
        Assert.Equal(planner.Time.GetUtcNow(), planner.Settings.UpdatesCheckedAt);
    }

    [Fact]
    public void BeforeAnyCheckTheCheckRowSaysGoalMakerChecksDaily()
    {
        Assert.Equal("Settings.UpdateCheckHint", Settings(ReleasesPage).UpdateCheckHint);
    }

    [Fact]
    public void PureBlackIsOnlyAvailableOutsideLightMode()
    {
        var settings = Settings(ReleasesPage);

        settings.SelectedMode = settings.ModeOptions.Single(option => (ThemeMode)option.Value == ThemeMode.Light);
        Assert.False(settings.IsPureBlackAvailable);
        Assert.Equal(ThemeMode.Light, planner.Settings.Appearance.Mode);

        settings.SelectedMode = settings.ModeOptions.Single(option => (ThemeMode)option.Value == ThemeMode.Dark);
        Assert.True(settings.IsPureBlackAvailable);
        Assert.Equal(["Settings.ThemeSystem", "Settings.ThemeLight", "Settings.ThemeDark"], settings.ModeOptions.Select(option => option.Text));
    }

    [Fact]
    public void ReduceMotionReachesThePageAsTheKitReadsIt()
    {
        var settings = Settings(ReleasesPage);
        Assert.Null(settings.PageReduceMotion);

        settings.SelectedMotion = settings.MotionOptions.Single(option => (ReduceMotion)option.Value == ReduceMotion.On);
        Assert.True(settings.PageReduceMotion);
        Assert.Equal(ReduceMotion.On, planner.Settings.Appearance.ReduceMotion);

        settings.SelectedMotion = settings.MotionOptions.Single(option => (ReduceMotion)option.Value == ReduceMotion.Off);
        Assert.False(settings.PageReduceMotion);
    }

    [Fact]
    public void PickingAThemeCardSwitchesTheTheme()
    {
        var settings = Settings(ReleasesPage);
        Assert.Equal("track", settings.SelectedTheme!.Id);

        settings.SelectedTheme = settings.Themes.Single(theme => theme.Id == "night");

        Assert.Equal("night", planner.Settings.Appearance.ThemeId);
        Assert.Equal("night", settings.SelectedTheme!.Id);
        Assert.Equal(4, settings.Themes.Count);
    }

    [Fact]
    public void TheDayStartAndTheReviewDayArePickedFromALists()
    {
        var settings = Settings(ReleasesPage);
        Assert.Equal(7, settings.DayStartOptions.Count);
        Assert.Equal(7, settings.WeekdayOptions.Count);
        Assert.Equal(1, (int)settings.WeekdayOptions[0].Value);

        settings.SelectedDayStart = settings.DayStartOptions[3];
        settings.SelectedWeeklyReviewDay = settings.WeekdayOptions[6];

        Assert.Equal(3, planner.Settings.DayStartHour);
        Assert.Equal(7, planner.Settings.WeeklyReviewWeekday);
        Assert.Same(settings.DayStartOptions[3], settings.SelectedDayStart);
    }

    [Fact]
    public void QuietHoursAreTypedTimesAndABadOneIsNeverSaved()
    {
        var settings = Settings(ReleasesPage);

        settings.QuietHoursStartText = "22:30";
        settings.QuietHoursEndText = "7:15";
        Assert.Equal(new QuietHours(new TimeOnly(22, 30), new TimeOnly(7, 15)), planner.Settings.QuietHours);
        Assert.Equal("07:15", settings.QuietHoursEndText);
        Assert.True(settings.IsQuietHoursOn);

        Assert.Equal("Settings.TimeInvalid", settings.ValidateTime("25:00"));
        Assert.Null(settings.ValidateTime("06:00"));
        settings.QuietHoursEndText = "later";
        Assert.Equal(new TimeOnly(7, 15), planner.Settings.QuietHours.End);

        settings.TurnOffQuietHoursCommand.Execute(null);
        Assert.True(planner.Settings.QuietHours.IsOff);
        Assert.False(settings.TurnOffQuietHoursCommand.CanExecute(null));
    }

    [Fact]
    public void AReminderTimeIsSavedInHalfHours()
    {
        var settings = Settings(ReleasesPage);
        settings.PlanReminderOn = true;
        Assert.Equal(40, settings.PlanReminderHalf);

        settings.PlanReminderHalf = 43;

        Assert.Equal(new TimeOnly(21, 30), planner.Settings.PlanTomorrowReminder);
        Assert.Equal(new TimeOnly(21, 30).ToString("t", System.Globalization.CultureInfo.CurrentCulture), SettingsViewModel.HalfHourText(43));
        // The reminder sliders show the time next to the thumb through the kit's ValueFormatter.
        Assert.Equal(SettingsViewModel.HalfHourText(43), SettingsViewModel.HalfHourFormatter(43));
    }

    [Fact]
    public void TheBackendMustBeAnHttpOrHttpsAddress()
    {
        var settings = Settings(ReleasesPage);

        Assert.Equal("Settings.BackendUrlInvalid", settings.ValidateBackendUrl("127.0.0.1:55321"));
        Assert.Null(settings.ValidateBackendUrl("http://127.0.0.1:55321"));
        Assert.Equal("Settings.BackendKeyInvalid", settings.ValidateBackendKey("  "));
    }

    [Fact]
    public async Task SigningOutAnywayIsAskedByItsRowNotTheViewModel()
    {
        var questions = new List<DangerQuestion>();
        var settings = Build(planner, planner.Strings, null, ReleasesPage, opened.Add, question =>
        {
            questions.Add(question);
            return false;
        });

        // The kit's danger row asks with this message before it runs the command.
        Assert.StartsWith("Settings.SignOutAnywayLost", settings.SignOutAnywayLost, StringComparison.Ordinal);

        await settings.SignOutAnywayCommand.ExecuteAsync(null);

        Assert.Empty(questions);
        Assert.False(settings.IsSigningOut);
        Assert.False(settings.HasSignOutWarning);
    }

    // The row in SettingsPage.xaml carries the confirmation in GoalMaker's words, Cancel included.
    [Fact]
    public void TheSignOutAnywayRowAsksInTheAppsWords()
    {
        var page = XDocument.Load(Path.Combine(RepositoryRoot(), "windows", "src", "GoalMaker.App", "Views", "SettingsPage.xaml"));

        var row = page.Descendants().Single(element => element.Name.LocalName == "DangerRow"
            && (string?)element.Attribute("Command") == "{Binding SignOutAnywayCommand}");

        Assert.Equal("{Binding SignOutAnywayLost}", (string?)row.Attribute("ConfirmMessage"));
        Assert.Equal("{DynamicResource Settings.SignOutAnywayAsk}", (string?)row.Attribute("ConfirmTitle"));
        Assert.Equal("{DynamicResource Settings.SignOutAnyway}", (string?)row.Attribute("ConfirmText"));
        Assert.Equal("{DynamicResource Settings.Cancel}", (string?)row.Attribute("CancelText"));
    }

    private static string RepositoryRoot()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "contracts")))
        {
            directory = directory.Parent;
        }

        return directory?.FullName ?? throw new DirectoryNotFoundException("contracts/ not found above " + AppContext.BaseDirectory);
    }

    [Fact]
    public void RestoringAsksFirstAndNoRestoresNothing()
    {
        var questions = new List<DangerQuestion>();
        var file = Path.Combine(Path.GetTempPath(), $"goalmaker-restore-{Guid.NewGuid():N}.json");
        var settings = Build(planner, planner.Strings, null, ReleasesPage, opened.Add, question =>
        {
            questions.Add(question);
            return false;
        }, pickImport: () => file);
        var backup = new BackupService(
            ContractResources.SyncedTables(), planner.Replica, () => TestPlanner.Owner, "1.0.0", "windows", planner.Time);
        File.WriteAllText(file, backup.Export());
        try
        {
            settings.RestoreFromFileCommand.Execute(null);
        }
        finally
        {
            File.Delete(file);
        }

        var asked = Assert.Single(questions);
        Assert.Equal("Settings.BackupRestoreAsk", asked.Title);
        Assert.StartsWith("Settings.BackupPreview", asked.Message, StringComparison.Ordinal);
        Assert.Equal("Settings.BackupRestoreGo", asked.Confirm);
        Assert.Null(settings.RestoreResult);
    }

    [Fact]
    public void AFileThatIsNotAnExportSaysWhyWithoutAsking()
    {
        var asked = 0;
        var file = Path.Combine(Path.GetTempPath(), $"goalmaker-restore-{Guid.NewGuid():N}.json");
        File.WriteAllText(file, "not json");
        var settings = Build(planner, planner.Strings, null, ReleasesPage, opened.Add, _ => ++asked > 0, pickImport: () => file);
        try
        {
            settings.RestoreFromFileCommand.Execute(null);
        }
        finally
        {
            File.Delete(file);
        }

        Assert.Equal(0, asked);
        Assert.Equal("Settings.BackupNotABackup", settings.RestoreResult);
        Assert.Equal(SettingsRowResult.Error, settings.RestoreResultKind);
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
        TestPlanner planner,
        GoalMaker.App.Localization.IStrings strings,
        UpdateService? service,
        string? releasesPage,
        Action<string> open,
        Func<DangerQuestion, bool>? confirm = null,
        Func<string?>? pickImport = null)
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
            new AutoUpdateCheck(updates, planner.Settings, planner.Time, AutoUpdateCheck.Daily),
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
            pickImport ?? (() => null),
            () => null,
            new NoSignInStartup(),
            new NoStartupProfiles(),
            new StartupProfilesRequest("com.goalmaker.app", "GoalMaker", "GoalMaker.exe"),
            () => { },
            releasesPage,
            open,
            confirm ?? (_ => false),
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

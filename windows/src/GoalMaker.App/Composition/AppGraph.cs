using System.IO;
using System.Net.Http;
using System.Net.NetworkInformation;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using System.Globalization;
using GoalMaker.Core.About;
using GoalMaker.Core.Assistant;
using GoalMaker.Core.Backup;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Problems;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Startup;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Activity;
using GoalMaker.Infrastructure.Assistant;
using GoalMaker.Infrastructure.Backup;
using GoalMaker.Infrastructure.Auth;
using GoalMaker.Infrastructure.Connector;
using GoalMaker.Infrastructure.Planning;
using GoalMaker.Infrastructure.Postgrest;
using GoalMaker.Infrastructure.Replica;
using GoalMaker.Infrastructure.Settings;
using GoalMaker.Infrastructure.Storage;
using GoalMaker.Infrastructure.Startup;
using GoalMaker.Infrastructure.Sync;
using GoalMaker.Infrastructure.Updates;
using Microsoft.Win32;

namespace GoalMaker.App.Composition;

/// <summary>
/// The one composition root: every adapter is created here and passed on through constructors
/// (CodePrint architecture rule). Lives as long as the process.
/// </summary>
public sealed class AppGraph : IDisposable
{
    private static readonly TimeSpan SyncDebounce = TimeSpan.FromSeconds(2);
    private static readonly TimeSpan SyncInterval = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan DayCheckInterval = TimeSpan.FromMinutes(1);
    private static readonly TimeSpan UpdateCheckDelay = TimeSpan.FromSeconds(30);
    private static readonly TimeSpan UpdateCheckLook = TimeSpan.FromHours(1);

    private readonly Supabase.Client supabase;
    private readonly IDisposable? signatureKey;
    private readonly bool localOnly;
    private readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(30) };

    // An installer is a few megabytes over whatever line the PC has, so it gets far longer than an API call.
    private readonly HttpClient updatesHttp = new() { Timeout = TimeSpan.FromMinutes(15) };

    // A chat message can take the model several tool rounds on the server, so it waits longer than a sync call.
    private readonly HttpClient assistantHttp = new() { Timeout = TimeSpan.FromSeconds(90) };
    private readonly SqliteReplica replica;
    private readonly SupabaseChangeFeed changeFeed;
    private readonly IProfileSettings profile;
    private readonly SyncedTableCatalog catalog;
    private readonly SessionSync sessionSync;
    private readonly Action<Action> runOnUi;
    private readonly IStrings strings;
    private readonly TickSound tick = new();
    private readonly ITimer dayCheck;
    private readonly TimerReminderScheduler reminderTimer;
    private readonly ToastReminderNotifications toasts;
    private DateOnly shownDay;
    private CancellationTokenSource? periodicSync;

    public AppGraph(
        BuildConfiguration build,
        IStrings strings,
        System.Windows.ResourceDictionary appResources,
        Action<Action> runOnUi,
        Action shutdownApp,
        Action restartApp,
        bool signIn = false,
        AppDataPaths? paths = null)
    {
        this.runOnUi = runOnUi;
        this.strings = strings;
        // The build's own folder; the start-up smoke test passes a throwaway one.
        Paths = paths ?? new AppDataPaths(build.IsDevBuild);
        Paths.EnsureRoot();
        Paths.ClearUpdates();
        Settings = new JsonSettingsStore(Paths.Settings);

        // A dev build skips sign-in and keeps its rows on this PC unless it is started with --sign-in; a
        // release build always signs in and syncs (docs/sign-in.md).
        localOnly = build.IsDevBuild && !signIn;
        var backend = (build.IsDevBuild ? Settings.BackendOverride : null) ?? build.DefaultBackend;
        AppInfo = new AppInfo(build.Version, build.IsDevBuild, backend, build.DefaultBackend, localOnly);
        var storedSession = new ProtectedFileSessionPersistence(Paths.Session);
        supabase = SupabaseClientFactory.Create(backend, storedSession);
        Auth = localOnly ? new LocalOnlyAuthGateway() : new SupabaseAuthGateway(supabase, storedSession);
        // This PC keeps its session for a week and then asks for the code again (docs/sign-in.md).
        SignInWatch = new SignInWatch(Auth, Settings, () => DateTimeOffset.Now);

        // Updates come from the repository's public GitHub Releases, with no sign-in (ADR 0010). The
        // signed manifest is what makes them trusted, so a build without the key has no channel.
        var configured = build.HasUpdateChannel;
        ISignatureVerifier signatures = configured
            ? new EcdsaSignatureVerifier(build.ManifestPublicKey)
            : new NotConfiguredSignatureVerifier();
        signatureKey = signatures as IDisposable;
        var releases = configured ? new ReleaseChannelAddress(build.UpdateUrl) : null;
        Updates = new UpdateService(
            build.Version,
            ReleasePlatform.Windows,
            configured,
            releases is null ? new NotConfiguredReleaseChannel() : new GitHubReleaseChannel(updatesHttp, releases, Paths.Updates),
            new ReleaseVerifier(signatures),
            new InstallerLauncher(shutdownApp));

        // The quiet check (docs/setup/signing-and-releases.md): half a minute after the start, then a
        // look every hour that checks once a day. It never installs, and a failure is only logged.
        UpdateChecks = new AutoUpdateCheck(Updates, Settings, TimeProvider.System, AutoUpdateCheck.Daily);
        UpdateChecks.Checked += (_, result) =>
        {
            if (result is UpdateCheckResult.Failed failed)
            {
                System.Diagnostics.Trace.WriteLine("GoalMaker: the update check failed: " + failed.Detail);
            }
        };
        UpdateChecks.Start(UpdateCheckDelay, UpdateCheckLook);

        catalog = ContractResources.SyncedTables();
        var design = ContractResources.Themes();
        replica = new SqliteReplica(localOnly ? Paths.LocalReplica : Paths.ReplicaFor(backend.Url), catalog, ReplicaMigrator.BuiltIn());
        // A call the server refuses the session for asks for a sync (built just below), which renews
        // the session or ends it.
        var postgrest = new PostgrestHttp(
            http, backend.Url, backend.PublishableKey, () => supabase.Auth.CurrentSession?.AccessToken, () => runOnUi(() => Sync?.Request()));
        IRemoteTables remote = localOnly ? new LocalOnlyRemoteTables(TimeProvider.System) : new PostgrestRemoteTables(postgrest);
        profile = new PostgrestProfileSettings(postgrest, () => (Auth.Session as AuthSession.SignedIn)?.UserId);
        Sync = new SyncCoordinator(
            new SyncEngine(catalog, replica, remote, TimeProvider.System, pulls: !localOnly, owner: () => (Auth.Session as AuthSession.SignedIn)?.UserId),
            replica,
            TimeProvider.System,
            SyncDebounce,
            Auth);
        sessionSync = new SessionSync(catalog, replica, Sync);
        var newRows = new NewRows(catalog, () => (Auth.Session as AuthSession.SignedIn)?.UserId, TimeProvider.System);
        Areas = new AreaList(replica, newRows, [.. design.AreaColors.Select(color => color.Id)], Sync.Request);
        Tags = new TagList(replica, newRows, Sync.Request);
        Projects = new ProjectList(replica, newRows, Sync.Request);
        Tasks = new TaskList(
            replica, newRows, Areas, Tags, Projects, Sync.Request, () => PlanningDay.Of(TimeProvider.System.GetLocalNow().DateTime, Settings.DayStartHour));
        Wants = new WantList(replica, newRows, Sync.Request, () => PlanningDay.Of(TimeProvider.System.GetLocalNow().DateTime, Settings.DayStartHour));
        Tally = new TallyList(replica, newRows, () => Settings.DeviceId, Sync.Request);
        var tallyDefaults = ContractResources.TallyDefaults();

        // Tally on this PC (docs/tally.md, ADR 0013): the window in front, sorted by the owner's rules and
        // the shipped ones. The raw log stays in the tally folder; only the day totals reach the replica.
        TallyTracker = new TallyTracker(
            new WindowsForegroundSource(),
            new DiskTallyLog(Paths.Tally),
            Tally,
            tallyDefaults.Rules,
            Projects.All,
            () => Settings.DayStartHour,
            TimeProvider.System);
        if (Settings.TallyOn)
        {
            TallyTracker.Start();
        }

        // Reminders (docs/reminders.md, ADR 0009): the replica decides, one timer in the tray app
        // carries the next one, and toasts show them with the same buttons as the phone.
        reminderTimer = new TimerReminderScheduler(TimeProvider.System, () => runOnUi(LookAtReminders));
        Rituals = new RitualRunList(replica, newRows, Sync.Request);
        Habits = new HabitList(replica, newRows, Sync.Request);
        ReminderRows = new ReminderList(replica, newRows, Sync.Request);
        Reminders = new ReminderService(
            ReminderRows,
            Tasks,
            reminderTimer,
            Settings,
            TimeProvider.System,
            Rituals,
            Wants,
            Habits);
        toasts = new ToastReminderNotifications(
            build.IsDevBuild ? "GoalMaker.Dev" : "GoalMaker",
            build.IsDevBuild ? strings.Get("App.Name") + " Dev" : strings.Get("App.Name"),
            Path.Combine(Paths.Root, "toast-icon.png"),
            strings);
        toasts.Activated += (_, activation) => runOnUi(() => OnToast(activation));
        SystemEvents.PowerModeChanged += OnPowerModeChanged;
        SystemEvents.TimeChanged += OnTimeChanged;

        // A sync can leave a series with two open occurrences; every device settles it the same way.
        // It can also move the next reminder, and settle ones on screen here that were handled on the
        // phone, so the timer is armed again and stale toasts come down.
        Sync.RunCompleted += (_, report) =>
        {
            if (report.Pulled > 0)
            {
                Tasks.RepairSeries();
            }

            if (report.Pulled > 0 || report.Pushed > 0)
            {
                runOnUi(SettleReminders);
            }
        };
        // What would not sync belongs in Settings, where it can be read and acted on, rather than
        // across the top of a list (docs/problems.md). A run that comes right clears it again.
        Sync.StatusChanged += (_, status) => runOnUi(() =>
        {
            if (status.State == SyncState.NeedsAttention)
            {
                Problems.Report(ProblemRules.Sync, status.Problem);
            }
            else if (status.State == SyncState.Idle)
            {
                Problems.Clear(ProblemRules.Sync);
            }
        });
        changeFeed = new SupabaseChangeFeed(supabase, catalog, Sync.Request);
        Auth.SessionChanged += (_, session) => OnSessionChanged(session);
        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        supabase.Auth.AddStateChangedListener((_, state) =>
        {
            if (state == Supabase.Gotrue.Constants.AuthState.TokenRefreshed && supabase.Auth.CurrentSession?.AccessToken is { } token)
            {
                changeFeed.UpdateToken(token);
            }
        });

        Theme = new ThemeApplier(design, appResources, ContractResources.Logo());
        // A dev build against the local stack reads the code the stack caught (docs/sign-in.md).
        var mailbox = build.IsDevBuild ? DevSignIn.MailboxOf(backend.Url) : null;
        Func<string, CancellationToken, Task<string?>>? devCode =
            mailbox is null ? null : new LocalMailbox(http, mailbox).CodeForAsync;
        SignIn = new SignInViewModel(Auth, SignInWatch, strings, build.IsDevBuild ? backend.Url : null, devCode);
        Shell = new ShellViewModel(Auth, SignIn, Problems, Updates, runOnUi);
        Places = new PlacesViewModel(Settings, strings);

        // The quick chat every composer shares (M7): the assistant function as the signed-in owner. An
        // answer asks for a sync at once, so what the chat changed shows in the lists.
        IAssistantClient assistant = new SupabaseAssistantClient(
            assistantHttp, backend.Url, backend.PublishableKey, () => supabase.Auth.CurrentSession?.AccessToken, () => runOnUi(() => Sync?.Request()));
        Chat = new ChatViewModel(
            assistant, Auth, Sync, Settings, strings, TimeProvider.System, runOnUi, () => _ = Sync.SyncNowAsync(CancellationToken.None), localOnly);

        // Each list's composer puts a line without a day on the list's own day (docs/composer.md).
        void OpenPlan() => PageRequested?.Invoke(this, AppPage.Plan);

        // A project item's chip in a list opens its project's board (docs/lists.md).
        void OpenProject(string id)
        {
            ProjectsPage.Select(id);
            PageRequested?.Invoke(this, AppPage.Projects);
        }

        void OpenWant(string title)
        {
            WantsPage.StartAdding(title);
            PageRequested?.Invoke(this, AppPage.Wants);
        }
        ComposerViewModel Composer(Func<DateOnly, DateOnly?> defaultDay) =>
            new(Tasks, Areas, Tags, Projects, Settings, strings, TimeProvider.System, Theme.AreaBrush, defaultDay, runOnUi, OpenPlan, OpenWant, Chat);
        ListViewModel List(ListKind kind, Func<DateOnly, DateOnly?> defaultDay) => new(
            kind,
            Tasks,
            Areas,
            Composer(defaultDay),
            Sync,
            Settings,
            strings,
            TimeProvider.System,
            Theme.AreaBrush,
            () => Theme.MotionReduced,
            tick,
            runOnUi,
            OpenPlan,
            Reminders,
            Tags,
            Filter,
            id => OpenTask(id, kind switch
            {
                ListKind.Tomorrow => AppPage.Tomorrow,
                ListKind.Inbox => AppPage.Inbox,
                _ => AppPage.Today,
            }),
            Filters,
            Goals,
            () => PageRequested?.Invoke(this, AppPage.Goals),
            HabitsPage,
            Habits,
            () => PageRequested?.Invoke(this, AppPage.Habits),
            kind == ListKind.Today ? () => OpenMini(MiniPage.Today) : null,
            Reviews,
            OpenReview,
            Projects,
            OpenProject);
        Filters = new ListFiltersViewModel(Areas, Tags, Filter, strings, Theme.AreaBrush, runOnUi);
        AreasPage = new AreasViewModel(Areas, Tags, strings, Theme.AreaBrush, runOnUi);
        Steps = new StepList(replica, newRows, Sync.Request);
        Goals = new GoalList(replica, newRows, Sync.Request);
        GoalsPage = new GoalsViewModel(Goals, Tasks, Settings, strings, TimeProvider.System, () => Theme.MotionReduced, runOnUi, Habits, Chat);
        HabitsPage = new HabitsViewModel(
            Habits, Goals, Settings, strings, TimeProvider.System, () => Theme.MotionReduced, runOnUi, () => OpenMini(MiniPage.Habits), Chat);
        Reviews = new ReviewList(replica, newRows, Sync.Request);
        // Tally's categories by name and palette color, the shipped ones and the owner's (docs/tally.md).
        TallyLabels NameTally(IReadOnlyList<TallyCategory> own) => new(tallyDefaults, own, strings, Theme.SwatchBrush);
        Review = new ReviewViewModel(
            ReviewRules.Weekly,
            ReviewRules.PeriodStart(ReviewRules.Weekly, PlanningDay.Of(TimeProvider.System.GetLocalNow().DateTime, Settings.DayStartHour)),
            Reviews,
            Tasks,
            Areas,
            Goals,
            Habits,
            ContractResources.Prompts(),
            Rituals,
            Settings,
            strings,
            TimeProvider.System,
            runOnUi,
            Tally,
            NameTally);
        ReviewsPage = new ReviewsViewModel(Reviews, Settings, strings, TimeProvider.System, OpenReview, runOnUi);
        WantsPage = new WantsViewModel(Wants, Settings, strings, TimeProvider.System, runOnUi, Chat);
        // A want added, decided or deleted moves the next alarm and may settle the wants toast.
        Wants.Changed += (_, _) => runOnUi(SettleReminders);
        TallyPage = new TallyViewModel(
            Tally, tallyDefaults, Projects, Settings, strings, TimeProvider.System, Theme.SwatchBrush,
            [.. design.AreaColors.Select(color => color.Id)], runOnUi, SwitchTally, TallyTracker.Stretches, TallyTracker.Recount);
        StatsPage = new StatsViewModel(Tasks, Goals, Habits, Reviews, Settings, strings, TimeProvider.System, runOnUi, Wants, Tally, NameTally, Projects);
        // Projects, the calendar and the archive each keep an area and tag filter of their own (docs/lists.md).
        ProjectsPage = new ProjectsViewModel(
            Projects,
            Tasks,
            Areas,
            Tags,
            Settings,
            strings,
            Theme.AreaBrush,
            id => OpenTask(id, AppPage.Projects),
            runOnUi,
            TimeProvider.System,
            form => ProjectItemWindow.Open(form, System.Windows.Application.Current?.MainWindow, Theme.Attach));
        CalendarPage = new CalendarViewModel(
            Tasks, ReminderRows, Areas, Tags, Projects, Settings, strings, Theme.AreaBrush, TimeProvider.System, id => OpenTask(id, AppPage.Calendar), runOnUi, OpenProject, HabitsPage, Habits);
        // The Places page that All places opens: a live tile for every place (ADR 0014).
        PlacesHub = new PlacesHubViewModel(
            Places, Tasks, HabitsPage, Habits, Goals, Reviews, Wants, Tally, NameTally, Settings, strings, TimeProvider.System, runOnUi);
        // An amount habit tapped on Today asks for its value on the Habits page.
        HabitsPage.LogRequested += (_, _) => PageRequested?.Invoke(this, AppPage.Habits);
        HabitsPage.PageWanted += (_, _) => PageRequested?.Invoke(this, AppPage.Habits);
        TaskDetail = new TaskDetailViewModel(
            Tasks, Areas, Tags, Steps, strings, TimeProvider.System, runOnUi, page => PageRequested?.Invoke(this, page), Goals, Settings);
        Archive = new ArchiveViewModel(Tasks, Areas, Tags, Projects, strings, Theme.AreaBrush, runOnUi, id => OpenTask(id, AppPage.Archive), OpenProject);
        QuickAdd = Composer(_ => null);
        TrayFlyout = new TrayFlyoutViewModel(
            Tasks,
            Areas,
            Settings,
            strings,
            TimeProvider.System,
            Theme.AreaBrush,
            runOnUi,
            () => WindowRequested?.Invoke(this, AppPage.Today),
            () => QuickAddRequested?.Invoke(this, EventArgs.Empty));
        Today = List(ListKind.Today, today => today);
        Tomorrow = List(ListKind.Tomorrow, today => today.AddDays(1));
        Inbox = List(ListKind.Inbox, _ => null);
        Plan = new PlanViewModel(
            Tasks,
            Areas,
            Composer(today => today.AddDays(1)),
            Settings,
            strings,
            TimeProvider.System,
            Theme.AreaBrush,
            tick,
            page => PageRequested?.Invoke(this, page),
            runOnUi,
            PlanTomorrowFinished);
        shownDay = PlanningDay.Of(DateTime.Now, Settings.DayStartHour);
        Theme.Applied += (_, _) => RefreshLists();
        dayCheck = TimeProvider.System.CreateTimer(_ => runOnUi(RefreshOnNewDay), null, DayCheckInterval, DayCheckInterval);
        // Your data (docs/backup.md): the export both apps read, and the weekly one into a folder.
        Backup = new BackupService(
            catalog, replica, () => (Auth.Session as AuthSession.SignedIn)?.UserId, AppInfo.Version, "windows", TimeProvider.System);
        var backupFolder = new DiskBackupFolder();
        Weekly = new WeeklyBackup(Backup, Settings, TimeProvider.System, backupFolder);
        // One a week, looked at after every sync run, so a PC that was off for three weeks writes
        // one at its next start rather than three (story 92).
        Sync.RunCompleted += (_, _) => runOnUi(() => OnWeeklyBackup(Weekly.Run()));

        // Startup: GoalMaker's own value under Run (story 81), and the ask that hands it to Startup
        // Profiles through that app's own window when it is installed (story 84).
        var executable = Environment.ProcessPath ?? string.Empty;
        SettingsPage = new SettingsViewModel(
            Auth, Sync, Settings, Updates, UpdateChecks, AppInfo, strings, Theme.Tokens, () => Theme.IsDark, Theme.Apply, PlanningDayChanged, Reminders.Rearm,
            gesture => ApplyQuickAddHotkey(gesture), OpenMini,
            Backup, Weekly, backupFolder, Sync.Request, PickExport, PickImport, PickFolder,
            new WindowsSignInStartup(build.InstanceName, executable),
            new WindowsStartupProfiles(),
            new StartupProfilesRequest("com.goalmaker.app", strings.Get("App.Name"), executable)
            {
                Arguments = "--tray",
                Publisher = build.Publisher,
                SupportsMinimized = true,
            },
            restartApp,
            releases?.ReleasesPage,
            OpenInBrowser,
            question => DotNetLib.Tray.TrayConfirmWindow.Ask(
                new DotNetLib.Tray.TrayConfirmation(question.Title, question.Message, question.Confirm, strings.Get("Settings.Cancel")),
                System.Windows.Application.Current?.MainWindow,
                Theme.Attach),
            runOnUi);

        ProblemsPage = new ProblemsViewModel(Problems, strings, runOnUi);

        // Where opening Settings lands, and its way from Areas and tags to the full Areas page.
        SettingsSections = new SettingsSectionsViewModel(() => Places.Open(PlacesViewModel.Areas));

        // The Claude connector's link and the activity log with undo (docs/connector.md, docs/activity.md), read online.
        Connector = new ConnectorViewModel(new PostgrestConnectorLinks(postgrest), backend.Url, strings, text => System.Windows.Clipboard.SetText(text));
        Activity = new ActivityViewModel(new PostgrestActivityLog(postgrest), strings, Sync.Request);
    }

    // Where an export is written, where one is read from, and the folder the weekly one uses. The
    // owner always picks: GoalMaker never writes outside what they chose.
    private static string? PickExport()
    {
        var dialog = new Microsoft.Win32.SaveFileDialog
        {
            FileName = BackupRules.FileName(DateOnly.FromDateTime(DateTime.Now).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)),
            Filter = "GoalMaker export (*.json)|*.json",
            DefaultExt = ".json",
            AddExtension = true,
        };
        return dialog.ShowDialog() == true ? dialog.FileName : null;
    }

    private static string? PickImport()
    {
        var dialog = new Microsoft.Win32.OpenFileDialog { Filter = "GoalMaker export (*.json)|*.json", CheckFileExists = true };
        return dialog.ShowDialog() == true ? dialog.FileName : null;
    }

    private static string? PickFolder()
    {
        var dialog = new Microsoft.Win32.OpenFolderDialog { Multiselect = false };
        return dialog.ShowDialog() == true ? dialog.FolderName : null;
    }

    // A web page the owner asked for opens in their default browser.
    private static void OpenInBrowser(string url) =>
        System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(url) { UseShellExecute = true })?.Dispose();

    /// <summary>Reading the owner's data out to a file and back in (docs/backup.md).</summary>
    public BackupService Backup { get; private set; } = null!;

    /// <summary>The weekly export into the folder the owner chose; the shell runs it after a sync.</summary>
    public WeeklyBackup Weekly { get; private set; } = null!;

    /// <summary>The Claude connector card in Settings.</summary>
    public ConnectorViewModel Connector { get; }

    /// <summary>Recent changes with undo.</summary>
    public ActivityViewModel Activity { get; }

    public AppDataPaths Paths { get; }

    public ISettingsStore Settings { get; }

    /// <summary>Holds this PC to its sign-in week (docs/sign-in.md).</summary>
    public SignInWatch SignInWatch { get; }

    public AppInfo AppInfo { get; }

    public IAuthGateway Auth { get; }

    public UpdateService Updates { get; }

    /// <summary>The quiet daily check for updates and the record of the last one.</summary>
    public AutoUpdateCheck UpdateChecks { get; }

    public SyncCoordinator Sync { get; }

    public AreaList Areas { get; }

    public TagList Tags { get; }

    public TaskList Tasks { get; }

    public ThemeApplier Theme { get; }

    /// <summary>The filter the three lists share (docs/lists.md).</summary>
    public ListFilterState Filter { get; } = new();

    /// <summary>The area and tag pickers above the lists.</summary>
    public ListFiltersViewModel Filters { get; private set; } = null!;

    /// <summary>The areas and tags manager.</summary>
    public AreasViewModel AreasPage { get; private set; } = null!;

    /// <summary>A task's details, loaded with the task a list opened.</summary>
    public TaskDetailViewModel TaskDetail { get; private set; } = null!;

    /// <summary>The archive of done tasks.</summary>
    public ArchiveViewModel Archive { get; private set; } = null!;

    public StepList Steps { get; private set; } = null!;

    /// <summary>The owner's goals and the amounts logged on them (docs/goals.md).</summary>
    public GoalList Goals { get; private set; } = null!;

    /// <summary>The Goals page.</summary>
    public GoalsViewModel GoalsPage { get; private set; } = null!;

    /// <summary>The owner's habits, their check-ins and pauses (docs/habits.md).</summary>
    public HabitList Habits { get; private set; } = null!;

    /// <summary>The Habits page.</summary>
    public HabitsViewModel HabitsPage { get; private set; } = null!;

    /// <summary>Which rituals ran on which planning day (docs/reminders.md, docs/reviews.md).</summary>
    public RitualRunList Rituals { get; private set; } = null!;

    /// <summary>The owner's weekly, monthly and yearly reviews (docs/reviews.md).</summary>
    public ReviewList Reviews { get; private set; } = null!;

    public WantList Wants { get; private set; } = null!;

    /// <summary>This PC's Tally totals and the owner's own categories and rules (docs/tally.md).</summary>
    public TallyList Tally { get; private set; } = null!;

    /// <summary>Follows the window in front while Tally is on, and writes this PC's day totals on the sync timer.</summary>
    public TallyTracker TallyTracker { get; private set; } = null!;

    /// <summary>The owner's projects and their milestones (docs/projects.md).</summary>
    public ProjectList Projects { get; private set; } = null!;

    /// <summary>The Reviews page.</summary>
    public ReviewsViewModel ReviewsPage { get; private set; } = null!;

    public WantsViewModel WantsPage { get; private set; } = null!;

    /// <summary>The Tally page (docs/tally.md): the switch, where the time went, and the owner's rules and categories.</summary>
    public TallyViewModel TallyPage { get; private set; } = null!;

    /// <summary>The Stats page (docs/stats.md).</summary>
    public StatsViewModel StatsPage { get; private set; } = null!;

    /// <summary>The Projects page (docs/projects.md).</summary>
    public ProjectsViewModel ProjectsPage { get; private set; } = null!;

    /// <summary>The Calendar page (docs/calendar.md).</summary>
    public CalendarViewModel CalendarPage { get; private set; } = null!;

    /// <summary>The Places page: every place as a live tile, with Edit for the pins.</summary>
    public PlacesHubViewModel PlacesHub { get; private set; } = null!;

    /// <summary>The guided review, opened from the Reviews page.</summary>
    public ReviewViewModel Review { get; private set; } = null!;

    public ReminderService Reminders { get; }

    /// <summary>The reminder rows themselves; the calendar reads them, the service arms them.</summary>
    public ReminderList ReminderRows { get; }

    /// <summary>The quick chat's switch and thread, shared by every composer (M7).</summary>
    public ChatViewModel Chat { get; }

    /// <summary>The composer behind the global quick-add box; its lines land in the Inbox unless they name a day.</summary>
    public ComposerViewModel QuickAdd { get; private set; } = null!;

    /// <summary>The tray's Today flyout.</summary>
    public TrayFlyoutViewModel TrayFlyout { get; private set; } = null!;

    /// <summary>Asks for the quick-add box (from the flyout); the app shows it.</summary>
    public event EventHandler? QuickAddRequested;

    /// <summary>Settings asked for a mini window (spec, story 80); the shell opens it.</summary>
    public event EventHandler<MiniPage>? MiniRequested;

    /// <summary>Opens a mini window from inside the app.</summary>
    public void OpenMini(MiniPage page) => MiniRequested?.Invoke(this, page);

    /// <summary>Registers the global quick-add shortcut; the app puts the real registration here at start-up.</summary>
    public Func<HotkeyGesture?, bool> ApplyQuickAddHotkey { get; set; } = _ => true;

    /// <summary>A reminder toast was clicked, so the main window should come up on this page.</summary>
    public event EventHandler<AppPage>? WindowRequested;

    /// <summary>A view model asks for a page (Plan tomorrow from a list, Today when the ritual ends); MainWindow opens it.</summary>
    public event EventHandler<AppPage>? PageRequested;

    public SignInViewModel SignIn { get; }

    public ShellViewModel Shell { get; }

    /// <summary>The sidebar's pinned places and Go to (ADR 0014).</summary>
    public PlacesViewModel Places { get; }

    /// <summary>What went wrong while nobody was watching (docs/problems.md).</summary>
    public ProblemLog Problems { get; } = new(TimeProvider.System);

    /// <summary>The problems card on the Settings page.</summary>
    public ProblemsViewModel ProblemsPage { get; }

    public SettingsSectionsViewModel SettingsSections { get; }

    public ListViewModel Today { get; }

    public ListViewModel Tomorrow { get; }

    public ListViewModel Inbox { get; }

    public PlanViewModel Plan { get; }

    public SettingsViewModel SettingsPage { get; }

    /// <summary>Shows a task's detail page; Back returns to <paramref name="from"/>.</summary>
    public void OpenTask(string id, AppPage from)
    {
        TaskDetail.Load(id, from);
        PageRequested?.Invoke(this, AppPage.Task);
    }

    public void Dispose()
    {
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
        SystemEvents.PowerModeChanged -= OnPowerModeChanged;
        SystemEvents.TimeChanged -= OnTimeChanged;
        reminderTimer.Dispose();
        UpdateChecks.Dispose();

        // The open stretch and the touched days go into the replica before it closes.
        TallyTracker.Dispose();

        // Nothing hears the buttons once GoalMaker has quit; the reminders wait in the replica.
        toasts.ClearAll();
        periodicSync?.Cancel();
        periodicSync?.Dispose();
        dayCheck.Dispose();
        tick.Dispose();
        _ = changeFeed.DisposeAsync().AsTask();
        Theme.Dispose();
        Sync.Dispose();
        replica.Dispose();
        signatureKey?.Dispose();
        supabase.Auth.Shutdown();
        http.Dispose();
        updatesHttp.Dispose();
        assistantHttp.Dispose();
    }

    // The weekly export runs unattended, so a folder that has gone or a file that would not write is
    // exactly what the problems area is for (docs/problems.md). Anything else is not worth a word.
    private void OnWeeklyBackup(WeeklyBackupResult result)
    {
        switch (result)
        {
            case WeeklyBackupResult.FolderGone:
                Problems.Report(ProblemRules.Backup, strings.Get("Problems.BackupFolderGone"));
                break;
            case WeeklyBackupResult.CouldNotWrite:
                Problems.Report(ProblemRules.Backup, strings.Get("Problems.BackupCouldNotWrite"));
                break;
            case WeeklyBackupResult.Written:
                Problems.Clear(ProblemRules.Backup);
                break;
        }
    }

    private void OnSessionChanged(AuthSession session)
    {
        periodicSync?.Cancel();
        periodicSync?.Dispose();
        periodicSync = null;
        // A sign-out leaves the replica and its outbox; a sign-in as someone else starts clean.
        sessionSync.Apply(session);
        if (session is not AuthSession.SignedIn)
        {
            _ = changeFeed.StopAsync();
            reminderTimer.Cancel();
            toasts.ClearAll();
            return;
        }

        runOnUi(LookAtReminders);
        periodicSync = new CancellationTokenSource();
        _ = SyncPeriodicallyAsync(periodicSync.Token);
        if (localOnly)
        {
            // Nothing leaves this PC: no Realtime, and the timer only writes Tally's day totals.
            return;
        }

        _ = UpdateProfileAsync();
        if (supabase.Auth.CurrentSession?.AccessToken is { } token)
        {
            _ = changeFeed.StartAsync(token);
        }
    }

    // Shows what arrived since the last look, including anything missed while the PC slept, and arms
    // the timer for the next one.
    private void LookAtReminders()
    {
        if (Auth.Session is not AuthSession.SignedIn)
        {
            return;
        }

        var look = Reminders.CatchUp();
        foreach (var reminder in look.Reminders)
        {
            toasts.Show(reminder);
        }

        if (look.WeeklyReview is { } weekly)
        {
            toasts.ShowReview(
                RitualRunList.WeeklyReview,
                weekly,
                monthly: false,
                letter: ReviewRules.LetterWaiting(ReviewRules.Weekly, weekly, Reviews.All()));
        }

        if (look.MonthlyReview is { } monthlyDay)
        {
            toasts.ShowReview(
                RitualRunList.MonthlyReview,
                monthlyDay,
                monthly: true,
                letter: ReviewRules.LetterWaiting(ReviewRules.Monthly, monthlyDay, Reviews.All()));
        }

        if (look.PlanTomorrow is { } day)
        {
            toasts.ShowPlanTomorrow(day);
        }

        if (look.Wants is { } ready)
        {
            var byId = Wants.All().ToDictionary(want => want.Id);
            toasts.ShowWants(ready, [.. ready.WantIds.Where(byId.ContainsKey).Select(id => byId[id].Title)]);
        }
    }

    private void SettleReminders()
    {
        Reminders.Rearm();
        foreach (var id in Reminders.Stale(toasts.Shown()))
        {
            toasts.Clear(id);
        }

        foreach (var (ritual, reviewDay) in toasts.ShownReviews().Where(shown => Reminders.ReviewStale(shown.Ritual, shown.Day)))
        {
            toasts.ClearReview(ritual, reviewDay);
        }

        foreach (var day in toasts.ShownPlanTomorrow().Where(Reminders.PlanTomorrowStale))
        {
            toasts.ClearPlanTomorrow(day);
        }

        ClearStaleWants();
    }

    // A wants toast goes once every want it names was decided, here or on the other device.
    private void ClearStaleWants()
    {
        foreach (var (wantsDay, ids) in toasts.ShownWants().Where(shown => Reminders.WantsStale(shown.Wants)))
        {
            toasts.ClearWants(wantsDay);
        }
    }

    // The ritual ran to the end: its evening reminder stays quiet that day on every device.
    private void PlanTomorrowFinished(DateOnly day)
    {
        Reminders.FinishPlanTomorrow(day);
        toasts.ClearPlanTomorrow(day);
    }

    private void OnToast(ToastActivation activation)
    {
        if (activation.PlanDay is { } planDay)
        {
            if (activation.Action == ToastAction.SkipPlan)
            {
                Reminders.SkipPlanTomorrow(planDay);
            }
            else
            {
                WindowRequested?.Invoke(this, AppPage.Plan);
            }

            toasts.ClearPlanTomorrow(planDay);
            return;
        }

        if (activation.Action == ToastAction.Wants)
        {
            WindowRequested?.Invoke(this, AppPage.Wants);
            return;
        }

        if (activation.Action is ToastAction.Review or ToastAction.SkipReview)
        {
            var (ritual, day) = activation.Review();
            if (ritual.Length > 0)
            {
                if (activation.Action == ToastAction.SkipReview)
                {
                    Reminders.FinishReview(ritual, day, skipped: true);
                }
                else
                {
                    var kind = ritual == RitualRunList.MonthlyReview ? ReviewRules.Monthly : ReviewRules.Weekly;
                    OpenReview(kind, ReviewReminder.PeriodStart(kind, day));
                    WindowRequested?.Invoke(this, AppPage.Review);
                }

                toasts.ClearReview(ritual, day);
            }

            return;
        }

        switch (activation.Action)
        {
            case ToastAction.Done:
                Reminders.Done(activation.ReminderId);
                break;
            case ToastAction.Snooze when activation.Snooze is { } option:
                Reminders.Snooze(activation.ReminderId, option);
                break;
            case ToastAction.Open:
                // Opening GoalMaker from a reminder counts as dismissing it (docs/reminders.md).
                Reminders.Dismiss(activation.ReminderId);
                WindowRequested?.Invoke(this, AppPage.Today);
                break;
            default:
                Reminders.Dismiss(activation.ReminderId);
                break;
        }

        toasts.Clear(activation.ReminderId);
    }

    // Tally follows the switch on its page at once; switching it off writes what it has.
    private void SwitchTally(bool on)
    {
        if (on)
        {
            TallyTracker.Start();
        }
        else
        {
            TallyTracker.Stop();
        }
    }

    // A timer doesn't run while the PC sleeps, and a changed clock moves every reminder.
    private void OnPowerModeChanged(object? sender, PowerModeChangedEventArgs e)
    {
        if (e.Mode == PowerModes.Resume)
        {
            runOnUi(LookAtReminders);
        }
    }

    private void OnTimeChanged(object? sender, EventArgs e) => runOnUi(LookAtReminders);

    // The lists move on when the planning day does (at the start hour, not midnight).
    private void RefreshOnNewDay()
    {
        if (PlanningDay.Of(DateTime.Now, Settings.DayStartHour) != shownDay)
        {
            RefreshLists();
        }
    }

    // The day start moves the lists here and "today" for the connector, which reads it from the profile.
    private void PlanningDayChanged()
    {
        RefreshLists();
        _ = UpdateProfileAsync();
    }

    // Best effort: offline, the next sign-in or day-start change writes it. The connector wants an IANA
    // zone id (Europe/Prague), which Windows' own ids (Central Europe Standard Time) convert to.
    private async Task UpdateProfileAsync()
    {
        // A dev build that stays on this PC has no profile on a server to keep.
        if (localOnly)
        {
            return;
        }

        // The region picks the zone's city (Central Europe Standard Time is Europe/Prague in Czechia).
        var zone = TimeZoneInfo.Local;
        var region = System.Globalization.RegionInfo.CurrentRegion.TwoLetterISORegionName;
        var iana = zone.HasIanaId ? zone.Id : TimeZoneInfo.TryConvertWindowsIdToIanaId(zone.Id, region, out var converted) ? converted : null;
        if (iana is null)
        {
            return;
        }

        try
        {
            await profile.UpdateAsync(iana, Settings.DayStartHour).ConfigureAwait(false);
        }
        catch (Exception error) when (error is RemoteUnavailableException or RemoteRejectedException)
        {
        }
    }

    private void RefreshLists()
    {
        shownDay = PlanningDay.Of(DateTime.Now, Settings.DayStartHour);
        Today.Refresh();
        Tomorrow.Refresh();
        Inbox.Refresh();
        Plan.Refresh();
        GoalsPage.Refresh();
        HabitsPage.Refresh();
        ReviewsPage.Refresh();
        WantsPage.Refresh();
        TallyPage.Refresh();
        StatsPage.Refresh();
        ProjectsPage.Refresh();
        CalendarPage.Refresh();
        PlacesHub.Refresh();
    }

    // Back online: flush the outbox now instead of waiting for the next offline retry.
    private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e)
    {
        if (e.IsAvailable && Auth.Session is AuthSession.SignedIn)
        {
            runOnUi(Sync.Request);
        }
    }

    private async Task SyncPeriodicallyAsync(CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(SyncInterval);
        try
        {
            while (await timer.WaitForNextTickAsync(cancellationToken).ConfigureAwait(false))
            {
                runOnUi(() =>
                {
                    // Tally's touched days are written first, so their totals go out with this run.
                    TallyTracker.Flush();
                    if (!localOnly)
                    {
                        Sync.Request();
                    }
                });
            }
        }
        catch (OperationCanceledException)
        {
            // Signed out or shutting down.
        }
    }

    // The Reviews page opens one period's review on the review page.
    private void OpenReview(string kind, DateOnly periodStart)
    {
        Review.Open(kind, periodStart);
        PageRequested?.Invoke(this, AppPage.Review);
    }
}

using System.IO;
using System.Net.Http;
using System.Net.NetworkInformation;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Activity;
using GoalMaker.Infrastructure.Auth;
using GoalMaker.Infrastructure.Connector;
using GoalMaker.Infrastructure.Planning;
using GoalMaker.Infrastructure.Postgrest;
using GoalMaker.Infrastructure.Replica;
using GoalMaker.Infrastructure.Settings;
using GoalMaker.Infrastructure.Storage;
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

    private readonly Supabase.Client supabase;
    private readonly IDisposable? signatureKey;
    private readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly SqliteReplica replica;
    private readonly SupabaseChangeFeed changeFeed;
    private readonly IProfileSettings profile;
    private readonly SyncedTableCatalog catalog;
    private readonly Action<Action> runOnUi;
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
        Action restartApp)
    {
        this.runOnUi = runOnUi;
        Paths = new AppDataPaths(build.IsDevBuild);
        Paths.EnsureRoot();
        Paths.ClearUpdates();
        Settings = new JsonSettingsStore(Paths.Settings);

        var backend = (build.IsDevBuild ? Settings.BackendOverride : null) ?? build.DefaultBackend;
        AppInfo = new AppInfo(build.Version, build.IsDevBuild, backend, build.DefaultBackend);
        supabase = SupabaseClientFactory.Create(backend, Paths.Session);
        Auth = new SupabaseAuthGateway(supabase);

        var configured = !string.IsNullOrWhiteSpace(build.ManifestPublicKey);
        ISignatureVerifier signatures = configured
            ? new EcdsaSignatureVerifier(build.ManifestPublicKey)
            : new NotConfiguredSignatureVerifier();
        signatureKey = signatures as IDisposable;
        Updates = new UpdateService(
            build.Version,
            ReleasePlatform.Windows,
            configured,
            new SupabaseReleaseChannel(supabase, Paths.Updates),
            new ReleaseVerifier(signatures),
            new InstallerLauncher(shutdownApp));

        catalog = ContractResources.SyncedTables();
        var design = ContractResources.Themes();
        replica = new SqliteReplica(Paths.ReplicaFor(backend.Url), catalog, ReplicaMigrator.BuiltIn());
        var postgrest = new PostgrestHttp(http, backend.Url, backend.PublishableKey, () => supabase.Auth.CurrentSession?.AccessToken);
        var remote = new PostgrestRemoteTables(postgrest);
        profile = new PostgrestProfileSettings(postgrest, () => (Auth.Session as AuthSession.SignedIn)?.UserId);
        Sync = new SyncCoordinator(new SyncEngine(catalog, replica, remote, TimeProvider.System), replica, TimeProvider.System, SyncDebounce);
        var newRows = new NewRows(catalog, () => (Auth.Session as AuthSession.SignedIn)?.UserId, TimeProvider.System);
        Areas = new AreaList(replica, newRows, [.. design.AreaColors.Select(color => color.Id)], Sync.Request);
        Tags = new TagList(replica, newRows, Sync.Request);
        Tasks = new TaskList(
            replica, newRows, Areas, Tags, Sync.Request, () => PlanningDay.Of(TimeProvider.System.GetLocalNow().DateTime, Settings.DayStartHour));

        // Reminders (docs/reminders.md, ADR 0009): the replica decides, one timer in the tray app
        // carries the next one, and toasts show them with the same buttons as the phone.
        reminderTimer = new TimerReminderScheduler(TimeProvider.System, () => runOnUi(LookAtReminders));
        Reminders = new ReminderService(
            new ReminderList(replica, newRows, Sync.Request),
            Tasks,
            reminderTimer,
            Settings,
            TimeProvider.System,
            new RitualRunList(replica, newRows, Sync.Request));
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
        SignIn = new SignInViewModel(Auth, strings, build.IsDevBuild ? backend.Url : null);
        Shell = new ShellViewModel(Auth, SignIn, runOnUi);

        // Each list's composer puts a line without a day on the list's own day (docs/composer.md).
        void OpenPlan() => PageRequested?.Invoke(this, AppPage.Plan);
        ComposerViewModel Composer(Func<DateOnly, DateOnly?> defaultDay) =>
            new(Tasks, Areas, Tags, Settings, strings, TimeProvider.System, Theme.AreaBrush, defaultDay, runOnUi, OpenPlan);
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
            () => PageRequested?.Invoke(this, AppPage.Goals));
        Filters = new ListFiltersViewModel(Areas, Tags, Filter, strings, Theme.AreaBrush, runOnUi);
        AreasPage = new AreasViewModel(Areas, Tags, strings, Theme.AreaBrush, runOnUi);
        Steps = new StepList(replica, newRows, Sync.Request);
        Goals = new GoalList(replica, newRows, Sync.Request);
        GoalsPage = new GoalsViewModel(Goals, Tasks, Settings, strings, TimeProvider.System, () => Theme.MotionReduced, runOnUi);
        TaskDetail = new TaskDetailViewModel(
            Tasks, Areas, Tags, Steps, strings, TimeProvider.System, runOnUi, page => PageRequested?.Invoke(this, page), Goals, Settings);
        Archive = new ArchiveViewModel(Tasks, strings, runOnUi, id => OpenTask(id, AppPage.Archive));
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
        SettingsPage = new SettingsViewModel(
            Auth, Sync, Settings, Updates, AppInfo, strings, Theme.Tokens, () => Theme.IsDark, Theme.Apply, PlanningDayChanged, Reminders.Rearm, gesture => ApplyQuickAddHotkey(gesture), restartApp, runOnUi);

        // The Claude connector's link and the activity log with undo (docs/connector.md, docs/activity.md), read online.
        Connector = new ConnectorViewModel(new PostgrestConnectorLinks(postgrest), backend.Url, strings, text => System.Windows.Clipboard.SetText(text));
        Activity = new ActivityViewModel(new PostgrestActivityLog(postgrest), strings, Sync.Request);
    }

    /// <summary>The Claude connector card in Settings.</summary>
    public ConnectorViewModel Connector { get; }

    /// <summary>Recent changes with undo.</summary>
    public ActivityViewModel Activity { get; }

    public AppDataPaths Paths { get; }

    public ISettingsStore Settings { get; }

    public AppInfo AppInfo { get; }

    public IAuthGateway Auth { get; }

    public UpdateService Updates { get; }

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

    public ReminderService Reminders { get; }

    /// <summary>The composer behind the global quick-add box; its lines land in the Inbox unless they name a day.</summary>
    public ComposerViewModel QuickAdd { get; private set; } = null!;

    /// <summary>The tray's Today flyout.</summary>
    public TrayFlyoutViewModel TrayFlyout { get; private set; } = null!;

    /// <summary>Asks for the quick-add box (from the flyout); the app shows it.</summary>
    public event EventHandler? QuickAddRequested;

    /// <summary>Registers the global quick-add shortcut; the app puts the real registration here at start-up.</summary>
    public Func<HotkeyGesture?, bool> ApplyQuickAddHotkey { get; set; } = _ => true;

    /// <summary>A reminder toast was clicked, so the main window should come up on this page.</summary>
    public event EventHandler<AppPage>? WindowRequested;

    /// <summary>A view model asks for a page (Plan tomorrow from a list, Today when the ritual ends); MainWindow opens it.</summary>
    public event EventHandler<AppPage>? PageRequested;

    public SignInViewModel SignIn { get; }

    public ShellViewModel Shell { get; }

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
    }

    private void OnSessionChanged(AuthSession session)
    {
        periodicSync?.Cancel();
        periodicSync?.Dispose();
        periodicSync = null;
        if (session is not AuthSession.SignedIn signedIn)
        {
            Sync.CancelScheduled();
            _ = changeFeed.StopAsync();
            reminderTimer.Cancel();
            toasts.ClearAll();
            return;
        }

        ForgetOtherAccounts(signedIn.UserId);
        Sync.Request();
        runOnUi(LookAtReminders);
        _ = UpdateProfileAsync();
        if (supabase.Auth.CurrentSession?.AccessToken is { } token)
        {
            _ = changeFeed.StartAsync(token);
        }

        periodicSync = new CancellationTokenSource();
        _ = SyncPeriodicallyAsync(periodicSync.Token);
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

        if (look.PlanTomorrow is { } day)
        {
            toasts.ShowPlanTomorrow(day);
        }
    }

    private void SettleReminders()
    {
        Reminders.Rearm();
        foreach (var id in Reminders.Stale(toasts.Shown()))
        {
            toasts.Clear(id);
        }

        foreach (var day in toasts.ShownPlanTomorrow().Where(Reminders.PlanTomorrowStale))
        {
            toasts.ClearPlanTomorrow(day);
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
    }

    // Back online: flush the outbox now instead of waiting for the next offline retry.
    private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e)
    {
        if (e.IsAvailable && Auth.Session is AuthSession.SignedIn)
        {
            runOnUi(Sync.Request);
        }
    }

    // A replica only ever holds one account's rows; signing in as someone else starts clean.
    private void ForgetOtherAccounts(string userId)
    {
        var foreign = catalog.Tables.Any(table => replica.All(table.Name)
            .Any(row => (string?)row[SyncedTable.OwnerId] is { } owner && owner != userId));
        if (foreign)
        {
            replica.ClearAll();
        }
    }

    private async Task SyncPeriodicallyAsync(CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(SyncInterval);
        try
        {
            while (await timer.WaitForNextTickAsync(cancellationToken).ConfigureAwait(false))
            {
                runOnUi(Sync.Request);
            }
        }
        catch (OperationCanceledException)
        {
            // Signed out or shutting down.
        }
    }
}

using System.Net.Http;
using System.Net.NetworkInformation;
using GoalMaker.App.Localization;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Auth;
using GoalMaker.Infrastructure.Replica;
using GoalMaker.Infrastructure.Settings;
using GoalMaker.Infrastructure.Storage;
using GoalMaker.Infrastructure.Sync;
using GoalMaker.Infrastructure.Updates;

namespace GoalMaker.App.Composition;

/// <summary>
/// The one composition root: every adapter is created here and passed on through constructors
/// (CodePrint architecture rule). Lives as long as the process.
/// </summary>
public sealed class AppGraph : IDisposable
{
    private static readonly TimeSpan SyncDebounce = TimeSpan.FromSeconds(2);
    private static readonly TimeSpan SyncInterval = TimeSpan.FromMinutes(5);

    private readonly Supabase.Client supabase;
    private readonly IDisposable? signatureKey;
    private readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly SqliteReplica replica;
    private readonly SupabaseChangeFeed changeFeed;
    private readonly SyncedTableCatalog catalog;
    private readonly Action<Action> runOnUi;
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
        var remote = new PostgrestRemoteTables(http, backend.Url, backend.PublishableKey, () => supabase.Auth.CurrentSession?.AccessToken);
        Sync = new SyncCoordinator(new SyncEngine(catalog, replica, remote, TimeProvider.System), replica, TimeProvider.System, SyncDebounce);
        var newRows = new NewRows(catalog, () => (Auth.Session as AuthSession.SignedIn)?.UserId, TimeProvider.System);
        Areas = new AreaList(replica, newRows, [.. design.AreaColors.Select(color => color.Id)], Sync.Request);
        Tags = new TagList(replica, newRows, Sync.Request);
        Tasks = new TaskList(replica, newRows, Areas, Tags, Sync.Request);
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

        Theme = new ThemeApplier(design, appResources);
        SignIn = new SignInViewModel(Auth, strings, build.IsDevBuild ? backend.Url : null);
        Shell = new ShellViewModel(Auth, SignIn, strings, runOnUi);
        Today = new TodayViewModel(Tasks, Areas, Tags, Sync, Auth, strings, TimeProvider.System, id => Theme.AreaBrush(id), runOnUi);
        SettingsPage = new SettingsViewModel(
            Auth, Sync, Settings, Updates, AppInfo, strings, Theme.Tokens, () => Theme.IsDark, Theme.Apply, restartApp, runOnUi);
    }

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

    public SignInViewModel SignIn { get; }

    public ShellViewModel Shell { get; }

    public TodayViewModel Today { get; }

    public SettingsViewModel SettingsPage { get; }

    public void Dispose()
    {
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
        periodicSync?.Cancel();
        periodicSync?.Dispose();
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
            return;
        }

        ForgetOtherAccounts(signedIn.UserId);
        Sync.Request();
        if (supabase.Auth.CurrentSession?.AccessToken is { } token)
        {
            _ = changeFeed.StartAsync(token);
        }

        periodicSync = new CancellationTokenSource();
        _ = SyncPeriodicallyAsync(periodicSync.Token);
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

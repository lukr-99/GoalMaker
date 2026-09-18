using GoalMaker.App.Localization;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Auth;
using GoalMaker.Infrastructure.Settings;
using GoalMaker.Infrastructure.Storage;
using GoalMaker.Infrastructure.Updates;

namespace GoalMaker.App.Composition;

/// <summary>
/// The one composition root: every adapter is created here and passed on through constructors
/// (CodePrint architecture rule). Lives as long as the process.
/// </summary>
public sealed class AppGraph : IDisposable
{
    private readonly Supabase.Client supabase;
    private readonly IDisposable? signatureKey;

    public AppGraph(
        BuildConfiguration build,
        IStrings strings,
        System.Windows.Media.Color brandAccent,
        Action<Action> runOnUi,
        Action shutdownApp,
        Action restartApp)
    {
        Paths = new AppDataPaths(build.IsDevBuild);
        Paths.EnsureRoot();
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

        Theme = new ThemeApplier(brandAccent);
        SignIn = new SignInViewModel(Auth, strings, build.IsDevBuild ? backend.Url : null);
        Shell = new ShellViewModel(Auth, SignIn, strings, runOnUi);
        SettingsPage = new SettingsViewModel(Auth, Settings, Updates, AppInfo, strings, Theme.Apply, restartApp, runOnUi);
    }

    public AppDataPaths Paths { get; }

    public ISettingsStore Settings { get; }

    public AppInfo AppInfo { get; }

    public IAuthGateway Auth { get; }

    public UpdateService Updates { get; }

    public ThemeApplier Theme { get; }

    public SignInViewModel SignIn { get; }

    public ShellViewModel Shell { get; }

    public SettingsViewModel SettingsPage { get; }

    public void Dispose()
    {
        signatureKey?.Dispose();
        supabase.Auth.Shutdown();
    }
}

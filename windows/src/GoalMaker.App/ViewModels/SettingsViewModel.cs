using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.About;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Updates;

namespace GoalMaker.App.ViewModels;

/// <summary>Appearance, account, updates, about and (dev builds) the backend switch.</summary>
public sealed partial class SettingsViewModel : ObservableObject
{
    private readonly IAuthGateway auth;
    private readonly ISettingsStore settings;
    private readonly UpdateService updates;
    private readonly AppInfo appInfo;
    private readonly IStrings strings;
    private readonly Action<ThemeMode> applyTheme;
    private readonly Action restartApp;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsSystemTheme), nameof(IsLightTheme), nameof(IsDarkTheme))]
    private ThemeMode themeMode;

    [ObservableProperty]
    private string email = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasUpdateStatus))]
    private string updateStatus = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsUpdateIdle))]
    [NotifyCanExecuteChangedFor(nameof(CheckForUpdatesCommand), nameof(InstallUpdateCommand))]
    private bool isUpdating;

    [ObservableProperty]
    private double downloadProgress;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanInstall), nameof(InstallText))]
    [NotifyCanExecuteChangedFor(nameof(InstallUpdateCommand))]
    private UpdateCheckResult.Available? availableUpdate;

    [ObservableProperty]
    private string backendUrlDraft;

    [ObservableProperty]
    private string backendKeyDraft;

    public SettingsViewModel(
        IAuthGateway auth,
        ISettingsStore settings,
        UpdateService updates,
        AppInfo appInfo,
        IStrings strings,
        Action<ThemeMode> applyTheme,
        Action restartApp,
        Action<Action> runOnUi)
    {
        this.auth = auth;
        this.settings = settings;
        this.updates = updates;
        this.appInfo = appInfo;
        this.strings = strings;
        this.applyTheme = applyTheme;
        this.restartApp = restartApp;
        themeMode = settings.ThemeMode;
        backendUrlDraft = appInfo.Backend.Url;
        backendKeyDraft = appInfo.Backend.PublishableKey;
        auth.SessionChanged += (_, session) => runOnUi(() => ShowSession(session));
        ShowSession(auth.Session);
    }

    public bool IsSystemTheme
    {
        get => ThemeMode == ThemeMode.System;
        set => SelectTheme(value, ThemeMode.System);
    }

    public bool IsLightTheme
    {
        get => ThemeMode == ThemeMode.Light;
        set => SelectTheme(value, ThemeMode.Light);
    }

    public bool IsDarkTheme
    {
        get => ThemeMode == ThemeMode.Dark;
        set => SelectTheme(value, ThemeMode.Dark);
    }

    public bool HasUpdateStatus => UpdateStatus.Length > 0;

    public bool IsUpdateIdle => !IsUpdating;

    public bool CanInstall => AvailableUpdate is not null;

    public string InstallText =>
        AvailableUpdate is null ? string.Empty : strings.Get("Settings.InstallUpdate", AvailableUpdate.Manifest.Version);

    public string VersionText => strings.Get("Settings.Version", appInfo.Version);

    public string BackendText => strings.Get("Settings.Backend", appInfo.Backend.Url);

    public bool IsDevBuild => appInfo.IsDevBuild;

    partial void OnThemeModeChanged(ThemeMode value)
    {
        settings.ThemeMode = value;
        applyTheme(value);
    }

    [RelayCommand]
    private Task SignOutAsync() => auth.SignOutAsync();

    [RelayCommand(CanExecute = nameof(IsUpdateIdle))]
    private async Task CheckForUpdatesAsync()
    {
        IsUpdating = true;
        AvailableUpdate = null;
        UpdateStatus = strings.Get("Settings.Update.Checking");
        try
        {
            var result = await updates.CheckAsync(CancellationToken.None);
            AvailableUpdate = result as UpdateCheckResult.Available;
            UpdateStatus = result switch
            {
                UpdateCheckResult.NotConfigured => strings.Get("Settings.Update.NotConfigured"),
                UpdateCheckResult.DevelopmentBuild => strings.Get("Settings.Update.DevBuild"),
                UpdateCheckResult.UpToDate upToDate => strings.Get("Settings.Update.UpToDate", upToDate.Latest),
                UpdateCheckResult.Available available => strings.Get("Settings.Update.Available", available.Manifest.Version),
                UpdateCheckResult.Untrusted => strings.Get("Settings.Update.Untrusted"),
                UpdateCheckResult.Failed failed => strings.Get("Settings.Update.Failed", failed.Detail),
                _ => string.Empty,
            };
        }
        finally
        {
            IsUpdating = false;
        }
    }

    private bool CanInstallUpdate() => CanInstall && !IsUpdating;

    [RelayCommand(CanExecute = nameof(CanInstallUpdate))]
    private async Task InstallUpdateAsync()
    {
        if (AvailableUpdate is not { } update)
        {
            return;
        }

        IsUpdating = true;
        var progress = new Progress<double>(value =>
        {
            DownloadProgress = value;
            UpdateStatus = strings.Get("Settings.Update.Downloading", value);
        });
        try
        {
            var result = await updates.InstallAsync(update, progress, CancellationToken.None);
            UpdateStatus = result switch
            {
                InstallResult.InstallerStarted => strings.Get("Settings.Update.Started"),
                InstallResult.DownloadCorrupted => strings.Get("Settings.Update.Corrupted"),
                InstallResult.Failed failed => strings.Get("Settings.Update.Failed", failed.Detail),
                _ => string.Empty,
            };
            AvailableUpdate = null;
        }
        finally
        {
            IsUpdating = false;
        }
    }

    [RelayCommand]
    private void SaveBackend()
    {
        if (!appInfo.IsDevBuild)
        {
            return;
        }

        var draft = new BackendEnvironment(BackendUrlDraft.Trim(), BackendKeyDraft.Trim());
        settings.BackendOverride = draft.IsConfigured && draft != appInfo.DefaultBackend ? draft : null;
        restartApp();
    }

    [RelayCommand]
    private void ResetBackend()
    {
        if (!appInfo.IsDevBuild)
        {
            return;
        }

        settings.BackendOverride = null;
        restartApp();
    }

    private void SelectTheme(bool selected, ThemeMode mode)
    {
        if (selected)
        {
            ThemeMode = mode;
        }
    }

    private void ShowSession(AuthSession session) =>
        Email = session is AuthSession.SignedIn signedIn ? signedIn.Email : string.Empty;
}

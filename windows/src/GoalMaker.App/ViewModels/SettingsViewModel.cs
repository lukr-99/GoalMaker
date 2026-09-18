using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.About;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Design;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Updates;

namespace GoalMaker.App.ViewModels;

/// <summary>Appearance, planning, account, updates, about and (dev builds) the backend switch.</summary>
public sealed partial class SettingsViewModel : ObservableObject
{
    private readonly IAuthGateway auth;
    private readonly SyncCoordinator sync;
    private readonly ISettingsStore settings;
    private readonly UpdateService updates;
    private readonly AppInfo appInfo;
    private readonly IStrings strings;
    private readonly DesignTokens design;
    private readonly Func<bool> isDark;
    private readonly Action<Appearance> applyAppearance;
    private readonly Action planningDayChanged;
    private readonly Action quietHoursChanged;
    private readonly Action restartApp;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsSystemTheme), nameof(IsLightTheme), nameof(IsDarkTheme), nameof(PureBlack))]
    [NotifyPropertyChangedFor(nameof(IsReduceMotionSystem), nameof(IsReduceMotionOn), nameof(IsReduceMotionOff))]
    [NotifyPropertyChangedFor(nameof(CompletionSound), nameof(Themes))]
    private Appearance appearance;

    [ObservableProperty]
    private string email = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSignOutWarning))]
    private string signOutWarning = string.Empty;

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
        SyncCoordinator sync,
        ISettingsStore settings,
        UpdateService updates,
        AppInfo appInfo,
        IStrings strings,
        DesignTokens design,
        Func<bool> isDark,
        Action<Appearance> applyAppearance,
        Action planningDayChanged,
        Action quietHoursChanged,
        Action restartApp,
        Action<Action> runOnUi)
    {
        this.auth = auth;
        this.sync = sync;
        this.settings = settings;
        this.updates = updates;
        this.appInfo = appInfo;
        this.strings = strings;
        this.design = design;
        this.isDark = isDark;
        this.applyAppearance = applyAppearance;
        this.planningDayChanged = planningDayChanged;
        this.quietHoursChanged = quietHoursChanged;
        this.restartApp = restartApp;
        appearance = settings.Appearance;
        backendUrlDraft = appInfo.Backend.Url;
        backendKeyDraft = appInfo.Backend.PublishableKey;
        auth.SessionChanged += (_, session) => runOnUi(() => ShowSession(session));
        ShowSession(auth.Session);
    }

    /// <summary>The theme cards, drawn for the mode in use; rebuilt whenever the appearance changes.</summary>
    public IReadOnlyList<ThemeOptionViewModel> Themes =>
        [.. design.Themes.Select(theme => new ThemeOptionViewModel(
            theme, isDark(), theme.Id == design.Theme(Appearance.ThemeId).Id, id => Appearance = Appearance with { ThemeId = id }))];

    public bool IsSystemTheme
    {
        get => Appearance.Mode == ThemeMode.System;
        set => SelectMode(value, ThemeMode.System);
    }

    public bool IsLightTheme
    {
        get => Appearance.Mode == ThemeMode.Light;
        set => SelectMode(value, ThemeMode.Light);
    }

    public bool IsDarkTheme
    {
        get => Appearance.Mode == ThemeMode.Dark;
        set => SelectMode(value, ThemeMode.Dark);
    }

    public bool PureBlack
    {
        get => Appearance.PureBlack;
        set => Appearance = Appearance with { PureBlack = value };
    }

    public bool IsReduceMotionSystem
    {
        get => Appearance.ReduceMotion == ReduceMotion.System;
        set => SelectReduceMotion(value, ReduceMotion.System);
    }

    public bool IsReduceMotionOn
    {
        get => Appearance.ReduceMotion == ReduceMotion.On;
        set => SelectReduceMotion(value, ReduceMotion.On);
    }

    public bool IsReduceMotionOff
    {
        get => Appearance.ReduceMotion == ReduceMotion.Off;
        set => SelectReduceMotion(value, ReduceMotion.Off);
    }

    public bool CompletionSound
    {
        get => Appearance.CompletionSound;
        set => Appearance = Appearance with { CompletionSound = value };
    }

    /// <summary>The start hours to pick from, 00:00 to 06:00; the index is the hour.</summary>
    public IReadOnlyList<string> DayStartHours { get; } =
        [.. Enumerable.Range(0, PlanningDay.LatestStartHour + 1).Select(hour => new TimeOnly(hour, 0).ToString("t", CultureInfo.CurrentCulture))];

    /// <summary>When the planning day starts (docs/lists.md); changing it moves the lists at once.</summary>
    public int DayStartHour
    {
        get => settings.DayStartHour;
        set
        {
            if (value < 0 || value == settings.DayStartHour)
            {
                return;
            }

            settings.DayStartHour = value;
            OnPropertyChanged();
            planningDayChanged();
        }
    }

    /// <summary>The hours quiet hours can start or end at, 00:00 to 23:00; the index is the hour.</summary>
    public IReadOnlyList<string> QuietHourChoices { get; } =
        [.. Enumerable.Range(0, 24).Select(hour => new TimeOnly(hour, 0).ToString("t", CultureInfo.CurrentCulture))];

    /// <summary>When quiet hours start (docs/reminders.md); the same hour as the end switches them off.</summary>
    public int QuietHoursStart
    {
        get => settings.QuietHours.Start.Hour;
        set => SetQuietHours(settings.QuietHours with { Start = new TimeOnly(Math.Clamp(value, 0, 23), 0) });
    }

    public int QuietHoursEnd
    {
        get => settings.QuietHours.End.Hour;
        set => SetQuietHours(settings.QuietHours with { End = new TimeOnly(Math.Clamp(value, 0, 23), 0) });
    }

    /// <summary>What the quiet hours do, in words.</summary>
    public string QuietHoursSummary => settings.QuietHours.IsOff
        ? strings.Get("Settings.QuietHoursOff")
        : strings.Get(
            "Settings.QuietHoursOn",
            settings.QuietHours.Start.ToString("t", CultureInfo.CurrentCulture),
            settings.QuietHours.End.ToString("t", CultureInfo.CurrentCulture));

    public bool HasUpdateStatus => UpdateStatus.Length > 0;

    // Quiet hours move ordinary reminders, so the reminder timer is armed again.
    private void SetQuietHours(QuietHours window)
    {
        if (window == settings.QuietHours)
        {
            return;
        }

        settings.QuietHours = window;
        OnPropertyChanged(nameof(QuietHoursStart));
        OnPropertyChanged(nameof(QuietHoursEnd));
        OnPropertyChanged(nameof(QuietHoursSummary));
        quietHoursChanged();
    }

    public bool HasSignOutWarning => SignOutWarning.Length > 0;

    public bool IsUpdateIdle => !IsUpdating;

    public bool CanInstall => AvailableUpdate is not null;

    public string InstallText =>
        AvailableUpdate is null ? string.Empty : strings.Get("Settings.InstallUpdate", AvailableUpdate.Manifest.Version);

    public string VersionText => strings.Get("Settings.Version", appInfo.Version);

    public string BackendText => strings.Get("Settings.Backend", appInfo.Backend.Url);

    public bool IsDevBuild => appInfo.IsDevBuild;

    partial void OnAppearanceChanged(Appearance value)
    {
        settings.Appearance = value;
        applyAppearance(value);
    }

    /// <summary>Pushes what's waiting, empties the replica, then signs out (docs/sync.md: Sign-out).</summary>
    [RelayCommand]
    private async Task SignOutAsync()
    {
        if (!await sync.FlushAndClearAsync(discardUnsynced: false, CancellationToken.None))
        {
            SignOutWarning = strings.Get("Settings.SignOutUnsynced", sync.Status.PendingChanges);
            return;
        }

        SignOutWarning = string.Empty;
        await auth.SignOutAsync();
    }

    [RelayCommand]
    private async Task SignOutAnywayAsync()
    {
        await sync.FlushAndClearAsync(discardUnsynced: true, CancellationToken.None);
        SignOutWarning = string.Empty;
        await auth.SignOutAsync();
    }

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

    private void SelectMode(bool selected, ThemeMode mode)
    {
        if (selected)
        {
            Appearance = Appearance with { Mode = mode };
        }
    }

    private void SelectReduceMotion(bool selected, ReduceMotion choice)
    {
        if (selected)
        {
            Appearance = Appearance with { ReduceMotion = choice };
        }
    }

    private void ShowSession(AuthSession session) =>
        Email = session is AuthSession.SignedIn signedIn ? signedIn.Email : string.Empty;
}

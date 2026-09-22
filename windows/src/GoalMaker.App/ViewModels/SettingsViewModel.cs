using System.Globalization;
using System.Windows.Input;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.Core.About;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Backup;
using GoalMaker.Core.Design;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Startup;
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
    private readonly Func<HotkeyGesture?, bool> applyQuickAddHotkey;
    private readonly Action<MiniPage> openMini;
    private readonly BackupService backup;
    private readonly WeeklyBackup weekly;
    private readonly Action requestSync;
    private readonly Func<string?> pickExport;
    private readonly Func<string?> pickImport;
    private readonly Func<string?> pickFolder;
    private readonly IBackupFolder folder;
    private readonly ISignInStartup signInStartup;
    private readonly IStartupProfiles startupProfiles;
    private readonly StartupProfilesRequest startupProfilesRequest;
    private readonly Action restartApp;
    private readonly string? releasesPage;
    private readonly Action<string> openInBrowser;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsSystemTheme), nameof(IsLightTheme), nameof(IsDarkTheme), nameof(PureBlack))]
    [NotifyPropertyChangedFor(nameof(IsReduceMotionSystem), nameof(IsReduceMotionOn), nameof(IsReduceMotionOff))]
    [NotifyPropertyChangedFor(nameof(CompletionSound), nameof(Themes))]
    private Appearance appearance;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(SignsInAgain), nameof(HasSignsInAgain))]
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
    private string startupProfilesStatus = string.Empty;

    [ObservableProperty]
    private string backupStatus = string.Empty;

    private string weeklyBackupFolder = string.Empty;
    private string? pending;

    private bool startsWithWindows;

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
        Func<HotkeyGesture?, bool> applyQuickAddHotkey,
        Action<MiniPage> openMini,
        BackupService backup,
        WeeklyBackup weekly,
        IBackupFolder folder,
        Action requestSync,
        Func<string?> pickExport,
        Func<string?> pickImport,
        Func<string?> pickFolder,
        ISignInStartup signInStartup,
        IStartupProfiles startupProfiles,
        StartupProfilesRequest startupProfilesRequest,
        Action restartApp,
        string? releasesPage,
        Action<string> openInBrowser,
        Action<Action> runOnUi)
    {
        this.openMini = openMini;
        this.backup = backup;
        this.weekly = weekly;
        this.folder = folder;
        this.requestSync = requestSync;
        this.pickExport = pickExport;
        this.pickImport = pickImport;
        this.pickFolder = pickFolder;
        weeklyBackupFolder = settings.WeeklyBackupFolder ?? string.Empty;
        this.signInStartup = signInStartup;
        this.startupProfiles = startupProfiles;
        this.startupProfilesRequest = startupProfilesRequest;
        startsWithWindows = signInStartup.IsOn;
        HasStartupProfiles = startupProfiles.Find() is not null;
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
        this.applyQuickAddHotkey = applyQuickAddHotkey;
        this.restartApp = restartApp;
        this.releasesPage = releasesPage;
        this.openInBrowser = openInBrowser;
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

    /// <summary>The times the evening reminder can ring at, every half hour; the index is the half hour of the day.</summary>
    public IReadOnlyList<string> PlanReminderChoices { get; } =
        [.. Enumerable.Range(0, 48).Select(half => new TimeOnly(half / 2, half % 2 * 30).ToString("t", CultureInfo.CurrentCulture))];

    /// <summary>Whether the evening Plan tomorrow reminder rings (docs/reminders.md); switching it on again starts at 20:00.</summary>
    public bool PlanReminderOn
    {
        get => settings.PlanTomorrowReminder is not null;
        set => SetPlanReminder(value ? settings.PlanTomorrowReminder ?? RitualReminder.DefaultTime : null);
    }

    /// <summary>When the evening reminder rings, as an index into <see cref="PlanReminderChoices"/>.</summary>
    public int PlanReminderTime
    {
        get => settings.PlanTomorrowReminder is { } time ? (time.Hour * 2) + (time.Minute >= 30 ? 1 : 0) : (RitualReminder.DefaultTime.Hour * 2);
        set
        {
            var half = Math.Clamp(value, 0, 47);
            SetPlanReminder(new TimeOnly(half / 2, half % 2 * 30));
        }
    }

    /// <summary>What the evening reminder does, in words.</summary>
    public string PlanReminderSummary => settings.PlanTomorrowReminder is { } at
        ? strings.Get("Settings.PlanReminderOn", at.ToString("t", CultureInfo.CurrentCulture))
        : strings.Get("Settings.PlanReminderOff");

    /// <summary>The weekdays the weekly review reminder can ring on, Monday first.</summary>
    public IReadOnlyList<string> ReviewWeekdays { get; } =
        [.. Enumerable.Range(0, 7).Select(day => CultureInfo.CurrentCulture.DateTimeFormat.DayNames[(day + 1) % 7])];

    /// <summary>Whether the weekly review reminder rings (docs/reviews.md); switching it on again starts at 18:00.</summary>
    public bool WeeklyReviewOn
    {
        get => settings.WeeklyReviewReminder is not null;
        set => SetWeeklyReview(value ? settings.WeeklyReviewReminder ?? ReviewReminder.DefaultTime : null);
    }

    /// <summary>When it rings, as an index into <see cref="PlanReminderChoices"/>.</summary>
    public int WeeklyReviewTime
    {
        get => settings.WeeklyReviewReminder is { } time ? (time.Hour * 2) + (time.Minute >= 30 ? 1 : 0) : ReviewReminder.DefaultTime.Hour * 2;
        set
        {
            var half = Math.Clamp(value, 0, 47);
            SetWeeklyReview(new TimeOnly(half / 2, half % 2 * 30));
        }
    }

    /// <summary>The weekday it rings on, as an index into <see cref="ReviewWeekdays"/> (0 is Monday).</summary>
    public int WeeklyReviewDay
    {
        get => Math.Clamp(settings.WeeklyReviewWeekday - 1, 0, 6);
        set
        {
            settings.WeeklyReviewWeekday = Math.Clamp(value, 0, 6) + 1;
            quietHoursChanged();
            OnPropertyChanged(nameof(WeeklyReviewDay));
            OnPropertyChanged(nameof(WeeklyReviewSummary));
        }
    }

    public string WeeklyReviewSummary => settings.WeeklyReviewReminder is { } at
        ? strings.Get("Settings.PlanReminderOn", at.ToString("t", CultureInfo.CurrentCulture))
        : strings.Get("Settings.WeeklyReviewHint");

    /// <summary>Whether the monthly review reminder rings, on the first day of a month.</summary>
    public bool MonthlyReviewOn
    {
        get => settings.MonthlyReviewReminder is not null;
        set => SetMonthlyReview(value ? settings.MonthlyReviewReminder ?? ReviewReminder.DefaultTime : null);
    }

    public int MonthlyReviewTime
    {
        get => settings.MonthlyReviewReminder is { } time ? (time.Hour * 2) + (time.Minute >= 30 ? 1 : 0) : ReviewReminder.DefaultTime.Hour * 2;
        set
        {
            var half = Math.Clamp(value, 0, 47);
            SetMonthlyReview(new TimeOnly(half / 2, half % 2 * 30));
        }
    }

    public string MonthlyReviewSummary => settings.MonthlyReviewReminder is { } at
        ? strings.Get("Settings.PlanReminderOn", at.ToString("t", CultureInfo.CurrentCulture))
        : strings.Get("Settings.MonthlyReviewHint");

    /// <summary>What the quiet hours do, in words.</summary>
    public string QuietHoursSummary => settings.QuietHours.IsOff
        ? strings.Get("Settings.QuietHoursOff")
        : strings.Get(
            "Settings.QuietHoursOn",
            settings.QuietHours.Start.ToString("t", CultureInfo.CurrentCulture),
            settings.QuietHours.End.ToString("t", CultureInfo.CurrentCulture));

    public bool HasUpdateStatus => UpdateStatus.Length > 0;

    /// <summary>The quick-add shortcut in use, as people write it, or that it's off.</summary>
    public string QuickAddHotkeyText { get; private set; } = string.Empty;

    /// <summary>Whether the shortcut works, or that another app already owns it.</summary>
    public string QuickAddHotkeyStatus { get; private set; } = string.Empty;

    /// <summary>Registers the stored shortcut (the default unless changed); the app calls it once at start-up.</summary>
    public void ApplyStoredQuickAddHotkey() => ApplyQuickAddHotkey(settings.QuickAddHotkey switch
    {
        null => HotkeyGesture.Default,
        "" => null,
        var text => HotkeyGesture.Parse(text) ?? HotkeyGesture.Default,
    });

    /// <summary>Keys pressed in the shortcut box become the shortcut, when they can make one.</summary>
    public bool RecordQuickAddHotkey(ModifierKeys modifiers, Key key)
    {
        if (HotkeyGesture.FromKeys(modifiers, key) is not { } gesture)
        {
            return false;
        }

        settings.QuickAddHotkey = gesture.ToString();
        ApplyQuickAddHotkey(gesture);
        return true;
    }

    /// <summary>
    /// GoalMaker starts in the tray when the owner signs in to Windows (spec, story 81), which is what
    /// keeps PC reminders coming. The installer sets this too, and both write the same value.
    /// </summary>
    public bool StartsWithWindows
    {
        get => startsWithWindows;
        set
        {
            if (startsWithWindows == value || !signInStartup.Set(value))
            {
                return;
            }

            startsWithWindows = value;
            OnPropertyChanged();
        }
    }

    /// <summary>Whether Startup Profiles is installed; the row that hands GoalMaker to it hides when not.</summary>
    public bool HasStartupProfiles { get; }

    /// <summary>
    /// Asks Startup Profiles to add GoalMaker (spec, story 84). It opens its own window, where the
    /// owner picks the profiles; GoalMaker writes nothing of theirs and learns nothing of the answer.
    /// </summary>
    [RelayCommand]
    private void AddToStartupProfiles() =>
        StartupProfilesStatus = strings.Get(startupProfiles.Ask(startupProfilesRequest)
            ? "Settings.StartupProfilesAsked"
            : "Settings.StartupProfilesFailed");

    /// <summary>The folder the weekly export writes into, or empty when it is off (story 92).</summary>
    public string WeeklyBackupFolder
    {
        get => weeklyBackupFolder;
        private set
        {
            weeklyBackupFolder = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasWeeklyBackup));
        }
    }

    public bool HasWeeklyBackup => WeeklyBackupFolder.Length > 0;

    /// <summary>Writes the whole export wherever the owner picks (story 91).</summary>
    [RelayCommand]
    private void ExportData()
    {
        if (pickExport() is not { } path)
        {
            return;
        }

        var text = backup.Export();
        BackupStatus = strings.Get(text is not null && folder.Write(
            System.IO.Path.GetDirectoryName(path) ?? string.Empty,
            System.IO.Path.GetFileName(path),
            text ?? string.Empty)
            ? "Settings.BackupExported"
            : "Settings.BackupNotWritten");
    }

    /// <summary>Reads a file and says what restoring it would do; RestoreData then does it.</summary>
    [RelayCommand]
    private void OfferRestore()
    {
        if (pickImport() is not { } path)
        {
            return;
        }

        var text = Read(path);
        if (text is null)
        {
            BackupStatus = strings.Get("Settings.BackupNotRead");
            return;
        }

        if (backup.Check(text) is { } problem)
        {
            pending = null;
            BackupStatus = strings.Get(Reason(problem));
            return;
        }

        pending = text;
        var preview = backup.Preview(text) ?? new RestoreReport();
        BackupStatus = strings.Get("Settings.BackupPreview", preview.Added, preview.Updated, preview.Kept);
        OnPropertyChanged(nameof(HasPendingRestore));
    }

    /// <summary>Restores the file the owner just looked at.</summary>
    [RelayCommand]
    private void RestoreData()
    {
        if (pending is not { } text)
        {
            return;
        }

        var report = backup.Restore(text, requestSync);
        pending = null;
        OnPropertyChanged(nameof(HasPendingRestore));
        BackupStatus = report is null
            ? strings.Get("Settings.BackupNotRead")
            : strings.Get("Settings.BackupRestored", report.Added, report.Updated, report.Kept);
    }

    /// <summary>True while a file has been read and checked but not restored yet.</summary>
    public bool HasPendingRestore => pending is not null;

    /// <summary>Picks the folder the weekly export writes into, and writes the first one now.</summary>
    [RelayCommand]
    private void ChooseWeeklyBackup()
    {
        if (pickFolder() is not { } chosen)
        {
            return;
        }

        settings.WeeklyBackupFolder = chosen;
        settings.WeeklyBackupWritten = null;
        WeeklyBackupFolder = chosen;
        BackupStatus = strings.Get(weekly.Run() == WeeklyBackupResult.Written
            ? "Settings.BackupWeeklyOn"
            : "Settings.BackupNotWritten");
    }

    /// <summary>Stops the weekly export; the files already written stay where they are.</summary>
    [RelayCommand]
    private void TurnOffWeeklyBackup()
    {
        settings.WeeklyBackupFolder = null;
        WeeklyBackupFolder = string.Empty;
        BackupStatus = strings.Get("Settings.BackupWeeklyOff");
    }

    private static string Reason(BackupProblem problem) => problem switch
    {
        BackupProblem.TooNew => "Settings.BackupTooNew",
        BackupProblem.AnotherOwner => "Settings.BackupAnotherOwner",
        BackupProblem.UnknownTable => "Settings.BackupUnknownTable",
        BackupProblem.RowWithoutId => "Settings.BackupBrokenRow",
        _ => "Settings.BackupNotABackup",
    };

    private static string? Read(string path)
    {
        try
        {
            return System.IO.File.ReadAllText(path);
        }
        catch (Exception error) when (error is System.IO.IOException or UnauthorizedAccessException)
        {
            return null;
        }
    }

    /// <summary>Opens the Today mini window (spec, story 80); the tray and `--mini today` do the same.</summary>
    [RelayCommand]
    private void OpenTodayMini() => openMini(MiniPage.Today);

    /// <summary>Opens the Habits mini window.</summary>
    [RelayCommand]
    private void OpenHabitsMini() => openMini(MiniPage.Habits);

    [RelayCommand]
    private void ResetQuickAddHotkey()
    {
        settings.QuickAddHotkey = null;
        ApplyQuickAddHotkey(HotkeyGesture.Default);
    }

    [RelayCommand]
    private void TurnOffQuickAddHotkey()
    {
        settings.QuickAddHotkey = string.Empty;
        ApplyQuickAddHotkey(null);
    }

    private void ApplyQuickAddHotkey(HotkeyGesture? gesture)
    {
        var accepted = applyQuickAddHotkey(gesture);
        QuickAddHotkeyText = gesture?.ToString() ?? strings.Get("Settings.QuickAddOff");
        QuickAddHotkeyStatus = gesture is null
            ? strings.Get("Settings.QuickAddOffHint")
            : strings.Get(accepted ? "Settings.QuickAddOn" : "Settings.QuickAddTaken", gesture.ToString());
        OnPropertyChanged(nameof(QuickAddHotkeyText));
        OnPropertyChanged(nameof(QuickAddHotkeyStatus));
    }

    // The evening reminder shares the reminder timer, so it is armed again.
    private void SetPlanReminder(TimeOnly? time)
    {
        if (time == settings.PlanTomorrowReminder)
        {
            return;
        }

        settings.PlanTomorrowReminder = time;
        OnPropertyChanged(nameof(PlanReminderOn));
        OnPropertyChanged(nameof(PlanReminderTime));
        OnPropertyChanged(nameof(PlanReminderSummary));
        quietHoursChanged();
    }

    // The review reminders ring on their own days, so the timer is armed again.
    private void SetWeeklyReview(TimeOnly? time)
    {
        if (time == settings.WeeklyReviewReminder)
        {
            return;
        }

        settings.WeeklyReviewReminder = time;
        OnPropertyChanged(nameof(WeeklyReviewOn));
        OnPropertyChanged(nameof(WeeklyReviewTime));
        OnPropertyChanged(nameof(WeeklyReviewSummary));
        quietHoursChanged();
    }

    private void SetMonthlyReview(TimeOnly? time)
    {
        if (time == settings.MonthlyReviewReminder)
        {
            return;
        }

        settings.MonthlyReviewReminder = time;
        OnPropertyChanged(nameof(MonthlyReviewOn));
        OnPropertyChanged(nameof(MonthlyReviewTime));
        OnPropertyChanged(nameof(MonthlyReviewSummary));
        quietHoursChanged();
    }

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

    /// <summary>
    /// The latest release's page, where the owner can always download GoalMaker themselves; shown
    /// whenever the build has an update channel, and most useful when a check fails.
    /// </summary>
    public bool HasReleasesPage => releasesPage is not null;

    public string InstallText =>
        AvailableUpdate is null ? string.Empty : strings.Get("Settings.InstallUpdate", AvailableUpdate.Manifest.Version);

    public string VersionText => strings.Get("Settings.Version", appInfo.Version);

    public string BackendText => strings.Get("Settings.Backend", appInfo.Backend.Url);

    public bool IsDevBuild => appInfo.IsDevBuild;

    /// <summary>A dev build that keeps everything on this PC has no account to show or leave.</summary>
    public bool IsLocalOnly => appInfo.LocalOnly;

    public bool HasAccount => !appInfo.LocalOnly;

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

    [RelayCommand(CanExecute = nameof(HasReleasesPage))]
    private void OpenReleasesPage()
    {
        if (releasesPage is not null)
        {
            openInBrowser(releasesPage);
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

    /// <summary>The day this PC asks for the code again, so the weekly sign-out is no surprise.</summary>
    public string SignsInAgain => auth.Session is AuthSession.SignedIn && settings.SignedInAt is { } moment
        ? strings.Get(
            "Settings.SignsInAgain",
            SignInPolicy.DueAt(moment).ToLocalTime().ToString("d MMMM", CultureInfo.CurrentCulture))
        : string.Empty;

    public bool HasSignsInAgain => SignsInAgain.Length > 0;

    private void ShowSession(AuthSession session) =>
        Email = session is AuthSession.SignedIn signedIn ? signedIn.Email : string.Empty;
}

using System.Globalization;
using System.Windows.Input;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using DotNetLib.Tray;
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

/// <summary>
/// Everything the Settings page's rows read and write (docs/design/spec.md, Settings): account,
/// appearance, planning and reminders, quick add, startup, mini windows, your data, updates, about and
/// (dev builds) the backend switch. Every change saves at once. A row's result (an export, a check, a
/// restore) is a text and a kind the row shows in place of its hint. The danger rows ask first, Cancel
/// focused: "Sign out anyway" through its row, Restore through <c>confirm</c> once the file is read.
/// </summary>
public sealed partial class SettingsViewModel : ObservableObject
{
    // A reminder's time is picked in half hours, 0 (00:00) to 47 (23:30).
    private const int LastHalfHour = 47;

    private readonly IAuthGateway auth;
    private readonly SyncCoordinator sync;
    private readonly ISettingsStore settings;
    private readonly UpdateService updates;
    private readonly AutoUpdateCheck updateChecks;
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
    private readonly Func<DangerQuestion, bool> confirm;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(SelectedMode), nameof(IsPureBlackAvailable), nameof(PureBlack))]
    [NotifyPropertyChangedFor(nameof(SelectedMotion), nameof(PageReduceMotion), nameof(CompletionSound), nameof(SelectedTheme))]
    private Appearance appearance;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(SignsInAgain))]
    private string email = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSignOutWarning), nameof(SignOutAnywayLost))]
    private string signOutWarning = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SignOutCommand), nameof(SignOutAnywayCommand))]
    private bool isSigningOut;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(UpdateResult))]
    private string updateStatus = string.Empty;

    [ObservableProperty]
    private SettingsRowResult updateStatusKind = SettingsRowResult.Success;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsUpdateIdle))]
    [NotifyCanExecuteChangedFor(nameof(CheckForUpdatesCommand), nameof(InstallUpdateCommand))]
    private bool isUpdating;

    /// <summary>While a check the owner started runs: the Check row's button says so and waits.</summary>
    [ObservableProperty]
    private bool isChecking;

    [ObservableProperty]
    private double downloadProgress;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanInstall), nameof(InstallText), nameof(AvailableText))]
    [NotifyCanExecuteChangedFor(nameof(InstallUpdateCommand))]
    private UpdateCheckResult.Available? availableUpdate;

    [ObservableProperty]
    private string? startupProfilesStatus;

    [ObservableProperty]
    private string? exportResult;

    [ObservableProperty]
    private SettingsRowResult exportResultKind;

    [ObservableProperty]
    private string? weeklyResult;

    [ObservableProperty]
    private SettingsRowResult weeklyResultKind;

    [ObservableProperty]
    private string? restoreResult;

    [ObservableProperty]
    private SettingsRowResult restoreResultKind;

    [ObservableProperty]
    private string backendUrlDraft;

    [ObservableProperty]
    private string backendKeyDraft;

    private IReadOnlyList<ThemeOptionViewModel> themes = [];
    private bool themesDark;
    private string weeklyBackupFolder;
    private bool startsWithWindows;

    public SettingsViewModel(
        IAuthGateway auth,
        SyncCoordinator sync,
        ISettingsStore settings,
        UpdateService updates,
        AutoUpdateCheck updateChecks,
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
        Func<DangerQuestion, bool> confirm,
        Action<Action> runOnUi)
    {
        this.auth = auth;
        this.sync = sync;
        this.settings = settings;
        this.updates = updates;
        this.updateChecks = updateChecks;
        this.appInfo = appInfo;
        this.strings = strings;
        this.design = design;
        this.isDark = isDark;
        this.applyAppearance = applyAppearance;
        this.planningDayChanged = planningDayChanged;
        this.quietHoursChanged = quietHoursChanged;
        this.applyQuickAddHotkey = applyQuickAddHotkey;
        this.openMini = openMini;
        this.backup = backup;
        this.weekly = weekly;
        this.folder = folder;
        this.requestSync = requestSync;
        this.pickExport = pickExport;
        this.pickImport = pickImport;
        this.pickFolder = pickFolder;
        this.signInStartup = signInStartup;
        this.startupProfiles = startupProfiles;
        this.startupProfilesRequest = startupProfilesRequest;
        this.restartApp = restartApp;
        this.releasesPage = releasesPage;
        this.openInBrowser = openInBrowser;
        this.confirm = confirm;
        weeklyBackupFolder = settings.WeeklyBackupFolder ?? string.Empty;
        startsWithWindows = signInStartup.IsOn;
        HasStartupProfiles = startupProfiles.Find() is not null;
        appearance = settings.Appearance;
        backendUrlDraft = appInfo.Backend.Url;
        backendKeyDraft = appInfo.Backend.PublishableKey;
        ModeOptions =
        [
            new(ThemeMode.System, strings.Get("Settings.ThemeSystem")),
            new(ThemeMode.Light, strings.Get("Settings.ThemeLight")),
            new(ThemeMode.Dark, strings.Get("Settings.ThemeDark")),
        ];
        MotionOptions =
        [
            new(ReduceMotion.System, strings.Get("Settings.ThemeSystem")),
            new(ReduceMotion.On, strings.Get("Settings.ReduceMotionOn")),
            new(ReduceMotion.Off, strings.Get("Settings.ReduceMotionOff")),
        ];
        BuildThemes();
        auth.SessionChanged += (_, session) => runOnUi(() => ShowSession(session));
        // The quiet daily check finds updates too, so the card follows what the service keeps.
        availableUpdate = updates.Waiting;
        updates.WaitingChanged += (_, _) => runOnUi(() =>
        {
            if (!IsUpdating)
            {
                AvailableUpdate = updates.Waiting;
            }
        });
        updateChecks.Checked += (_, _) => runOnUi(() =>
        {
            OnPropertyChanged(nameof(LastCheckedText));
            OnPropertyChanged(nameof(HasLastChecked));
            OnPropertyChanged(nameof(UpdateCheckHint));
        });
        ShowSession(auth.Session);
    }

    // ---------------------------------------------------------------- Appearance

    /// <summary>The theme cards, drawn for the mode in use; drawn again only when light or dark changes, so the pick stays put.</summary>
    public IReadOnlyList<ThemeOptionViewModel> Themes => themes;

    /// <summary>The card of the theme in use; picking another card switches the theme.</summary>
    public ThemeOptionViewModel? SelectedTheme
    {
        get => themes.FirstOrDefault(theme => theme.Id == design.Theme(Appearance.ThemeId).Id);
        set
        {
            if (value is not null && value.Id != design.Theme(Appearance.ThemeId).Id)
            {
                Appearance = Appearance with { ThemeId = value.Id };
            }
        }
    }

    /// <summary>System, Light and Dark.</summary>
    public IReadOnlyList<SettingsOption> ModeOptions { get; }

    public SettingsOption SelectedMode
    {
        get => ModeOptions.First(option => (ThemeMode)option.Value == Appearance.Mode);
        set
        {
            if (value?.Value is ThemeMode mode && mode != Appearance.Mode)
            {
                Appearance = Appearance with { Mode = mode };
            }
        }
    }

    /// <summary>Pure black only shows in dark mode, so in Light the row is disabled and says why.</summary>
    public bool IsPureBlackAvailable => Appearance.Mode != ThemeMode.Light;

    public bool PureBlack
    {
        get => Appearance.PureBlack;
        set => Appearance = Appearance with { PureBlack = value };
    }

    /// <summary>Follow Windows, On and Off.</summary>
    public IReadOnlyList<SettingsOption> MotionOptions { get; }

    public SettingsOption SelectedMotion
    {
        get => MotionOptions.First(option => (ReduceMotion)option.Value == Appearance.ReduceMotion);
        set
        {
            if (value?.Value is ReduceMotion motion && motion != Appearance.ReduceMotion)
            {
                Appearance = Appearance with { ReduceMotion = motion };
            }
        }
    }

    /// <summary>The page's own reduce motion: on or off by the switch, null to follow Windows.</summary>
    public bool? PageReduceMotion => Appearance.ReduceMotion switch
    {
        ReduceMotion.On => true,
        ReduceMotion.Off => false,
        _ => null,
    };

    public bool CompletionSound
    {
        get => Appearance.CompletionSound;
        set => Appearance = Appearance with { CompletionSound = value };
    }

    // ---------------------------------------------------------------- Planning day and reminders

    /// <summary>The hours the day can start at, 00:00 to 06:00.</summary>
    public IReadOnlyList<SettingsOption> DayStartOptions { get; } =
        [.. Enumerable.Range(0, PlanningDay.LatestStartHour + 1).Select(hour => new SettingsOption(hour, new TimeOnly(hour, 0).ToString("t", CultureInfo.CurrentCulture)))];

    /// <summary>When the planning day starts (docs/lists.md); changing it moves the lists at once.</summary>
    public SettingsOption? SelectedDayStart
    {
        get => DayStartOptions.FirstOrDefault(option => (int)option.Value == settings.DayStartHour);
        set
        {
            if (value?.Value is not int hour || hour == settings.DayStartHour)
            {
                return;
            }

            settings.DayStartHour = hour;
            OnPropertyChanged();
            planningDayChanged();
        }
    }

    /// <summary>Whether the evening Plan tomorrow reminder rings (docs/reminders.md); switching it on again starts at 20:00.</summary>
    public bool PlanReminderOn
    {
        get => settings.PlanTomorrowReminder is not null;
        set => SetPlanReminder(value ? settings.PlanTomorrowReminder ?? RitualReminder.DefaultTime : null);
    }

    /// <summary>When the evening reminder rings, in half hours; the slider saves it on release.</summary>
    public double PlanReminderHalf
    {
        get => Half(settings.PlanTomorrowReminder ?? RitualReminder.DefaultTime);
        set => SetPlanReminder(FromHalf(value));
    }

    /// <summary>What the evening reminder does, in words.</summary>
    public string PlanReminderSummary => settings.PlanTomorrowReminder is { } at
        ? strings.Get("Settings.PlanReminderOn", at.ToString("t", CultureInfo.CurrentCulture))
        : strings.Get("Settings.PlanReminderOff");

    /// <summary>Whether the weekly review reminder rings (docs/reviews.md); switching it on again starts at 18:00.</summary>
    public bool WeeklyReviewOn
    {
        get => settings.WeeklyReviewReminder is not null;
        set => SetWeeklyReview(value ? settings.WeeklyReviewReminder ?? ReviewReminder.DefaultTime : null);
    }

    public double WeeklyReviewHalf
    {
        get => Half(settings.WeeklyReviewReminder ?? ReviewReminder.DefaultTime);
        set => SetWeeklyReview(FromHalf(value));
    }

    /// <summary>The weekdays the weekly review can ring on, Monday first; the value is 1 (Monday) to 7 (Sunday).</summary>
    public IReadOnlyList<SettingsOption> WeekdayOptions { get; } =
        [.. Enumerable.Range(1, 7).Select(day => new SettingsOption(day, CultureInfo.CurrentCulture.DateTimeFormat.DayNames[day % 7]))];

    public SettingsOption SelectedWeeklyReviewDay
    {
        get => WeekdayOptions[Math.Clamp(settings.WeeklyReviewWeekday, 1, 7) - 1];
        set
        {
            if (value?.Value is not int day || day == settings.WeeklyReviewWeekday)
            {
                return;
            }

            settings.WeeklyReviewWeekday = Math.Clamp(day, 1, 7);
            quietHoursChanged();
            OnPropertyChanged();
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

    public double MonthlyReviewHalf
    {
        get => Half(settings.MonthlyReviewReminder ?? ReviewReminder.DefaultTime);
        set => SetMonthlyReview(FromHalf(value));
    }

    public string MonthlyReviewSummary => settings.MonthlyReviewReminder is { } at
        ? strings.Get("Settings.PlanReminderOn", at.ToString("t", CultureInfo.CurrentCulture))
        : strings.Get("Settings.MonthlyReviewHint");

    /// <summary>Whether the toast for wants that became ready rings (docs/wants.md).</summary>
    public bool WantsReadyOn
    {
        get => settings.WantsReadyReminder is not null;
        set => SetWantsReady(value ? settings.WantsReadyReminder ?? WantReminder.DefaultTime : null);
    }

    public double WantsReadyHalf
    {
        get => Half(settings.WantsReadyReminder ?? WantReminder.DefaultTime);
        set => SetWantsReady(FromHalf(value));
    }

    public string WantsReadySummary => settings.WantsReadyReminder is { } at
        ? strings.Get("Settings.PlanReminderOn", at.ToString("t", CultureInfo.CurrentCulture))
        : strings.Get("Settings.WantsReadyHint");

    /// <summary>When quiet hours start (docs/reminders.md), typed as a time; the same time as the end switches them off.</summary>
    public string QuietHoursStartText
    {
        get => SettingsFieldRules.Format(settings.QuietHours.Start);
        set
        {
            if (SettingsFieldRules.Time(value) is { } start)
            {
                SetQuietHours(settings.QuietHours with { Start = start });
            }
        }
    }

    public string QuietHoursEndText
    {
        get => SettingsFieldRules.Format(settings.QuietHours.End);
        set
        {
            if (SettingsFieldRules.Time(value) is { } end)
            {
                SetQuietHours(settings.QuietHours with { End = end });
            }
        }
    }

    /// <summary>Checks a typed time on Enter or when the field is left: an error, or null for a good one.</summary>
    public Func<string, string?> ValidateTime => text => SettingsFieldRules.Time(text) is null ? strings.Get("Settings.TimeInvalid") : null;

    public bool IsQuietHoursOn => !settings.QuietHours.IsOff;

    /// <summary>What the quiet hours do, in words.</summary>
    public string QuietHoursSummary => settings.QuietHours.IsOff
        ? strings.Get("Settings.QuietHoursOff")
        : strings.Get(
            "Settings.QuietHoursOn",
            settings.QuietHours.Start.ToString("t", CultureInfo.CurrentCulture),
            settings.QuietHours.End.ToString("t", CultureInfo.CurrentCulture));

    /// <summary>A reminder time in half hours as the slider shows it.</summary>
    public static string HalfHourText(double half) => FromHalf(half).ToString("t", CultureInfo.CurrentCulture);

    /// <summary><see cref="HalfHourText"/> as the reminder sliders' value: "20:30" next to the thumb, also while it moves.</summary>
    public static Func<double, string> HalfHourFormatter { get; } = HalfHourText;

    [RelayCommand(CanExecute = nameof(IsQuietHoursOn))]
    private void TurnOffQuietHours() => SetQuietHours(QuietHours.Off);

    // ---------------------------------------------------------------- Quick add

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

    // ---------------------------------------------------------------- Startup and mini windows

    /// <summary>
    /// GoalMaker starts in the tray when the owner signs in to Windows (spec, story 81), which is what
    /// keeps PC reminders coming. The installer sets this too, and both write the same value. When
    /// Windows refuses, the switch goes back to what is really set.
    /// </summary>
    public bool StartsWithWindows
    {
        get => startsWithWindows;
        set
        {
            if (startsWithWindows != value && signInStartup.Set(value))
            {
                startsWithWindows = value;
            }

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

    /// <summary>Opens the Today mini window (spec, story 80); the tray and `--mini today` do the same.</summary>
    [RelayCommand]
    private void OpenTodayMini() => openMini(MiniPage.Today);

    /// <summary>Opens the Habits mini window.</summary>
    [RelayCommand]
    private void OpenHabitsMini() => openMini(MiniPage.Habits);

    // ---------------------------------------------------------------- Your data

    /// <summary>The folder the weekly export writes into, or empty when it is off (story 92).</summary>
    public string WeeklyBackupFolder
    {
        get => weeklyBackupFolder;
        private set
        {
            weeklyBackupFolder = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasWeeklyBackup));
            OnPropertyChanged(nameof(WeeklyBackupHint));
        }
    }

    public bool HasWeeklyBackup => WeeklyBackupFolder.Length > 0;

    /// <summary>Where the weekly export goes while it is on, or what it would do.</summary>
    public string WeeklyBackupHint => HasWeeklyBackup
        ? strings.Get("Settings.BackupWeeklyFolder", WeeklyBackupFolder)
        : strings.Get("Settings.BackupWeeklyHint");

    /// <summary>Writes the whole export wherever the owner picks (story 91).</summary>
    [RelayCommand]
    private void ExportData()
    {
        if (pickExport() is not { } path)
        {
            return;
        }

        var text = backup.Export();
        var written = text is not null && folder.Write(
            System.IO.Path.GetDirectoryName(path) ?? string.Empty,
            System.IO.Path.GetFileName(path),
            text);
        ExportResult = strings.Get(written ? "Settings.BackupExported" : "Settings.BackupNotWritten");
        ExportResultKind = written ? SettingsRowResult.Success : SettingsRowResult.Error;
    }

    /// <summary>
    /// Reads a file, checks it, says what restoring it would do and asks first (Cancel focused); only a
    /// yes restores. A file that is not a good export says why and restores nothing.
    /// </summary>
    [RelayCommand]
    private void RestoreFromFile()
    {
        if (pickImport() is not { } path)
        {
            return;
        }

        var text = Read(path);
        if (text is null)
        {
            ShowRestore("Settings.BackupNotRead", failed: true);
            return;
        }

        if (backup.Check(text) is { } problem)
        {
            ShowRestore(Reason(problem), failed: true);
            return;
        }

        var preview = backup.Preview(text) ?? new RestoreReport();
        var question = new DangerQuestion(
            strings.Get("Settings.BackupRestoreAsk"),
            strings.Get("Settings.BackupPreview", preview.Added, preview.Updated, preview.Kept),
            strings.Get("Settings.BackupRestoreGo"));
        if (!confirm(question))
        {
            return;
        }

        var report = backup.Restore(text, requestSync);
        if (report is null)
        {
            ShowRestore("Settings.BackupNotRead", failed: true);
            return;
        }

        RestoreResult = strings.Get("Settings.BackupRestored", report.Added, report.Updated, report.Kept);
        RestoreResultKind = SettingsRowResult.Success;
    }

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
        var written = weekly.Run() == WeeklyBackupResult.Written;
        WeeklyResult = strings.Get(written ? "Settings.BackupWeeklyOn" : "Settings.BackupNotWritten");
        WeeklyResultKind = written ? SettingsRowResult.Success : SettingsRowResult.Error;
    }

    /// <summary>Stops the weekly export; the files already written stay where they are.</summary>
    [RelayCommand]
    private void TurnOffWeeklyBackup()
    {
        settings.WeeklyBackupFolder = null;
        WeeklyBackupFolder = string.Empty;
        WeeklyResult = strings.Get("Settings.BackupWeeklyOff");
        WeeklyResultKind = SettingsRowResult.Success;
    }

    private void ShowRestore(string key, bool failed)
    {
        RestoreResult = strings.Get(key);
        RestoreResultKind = failed ? SettingsRowResult.Error : SettingsRowResult.Success;
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

    // ---------------------------------------------------------------- Account

    /// <summary>A dev build that keeps everything on this PC has no account to show or leave.</summary>
    public bool IsLocalOnly => appInfo.LocalOnly;

    public bool HasAccount => !appInfo.LocalOnly;

    /// <summary>The day this PC asks for the code again, so the weekly sign-out is no surprise.</summary>
    public string? SignsInAgain => auth.Session is AuthSession.SignedIn && settings.SignedInAt is { } moment
        ? strings.Get(
            "Settings.SignsInAgain",
            SignInPolicy.DueAt(moment).ToLocalTime().ToString("d MMMM", CultureInfo.CurrentCulture))
        : null;

    /// <summary>While changes haven't reached the server, "Sign out anyway" shows in the danger zone.</summary>
    public bool HasSignOutWarning => SignOutWarning.Length > 0;

    /// <summary>What "Sign out anyway" says in its confirmation before it acts: how many changes go.</summary>
    public string SignOutAnywayLost => strings.Get("Settings.SignOutAnywayLost", sync.Status.PendingChanges);

    private bool CanSignOut() => !IsSigningOut;

    /// <summary>Pushes what's waiting, empties the replica, then signs out (docs/sync.md: Sign-out).</summary>
    [RelayCommand(CanExecute = nameof(CanSignOut))]
    private async Task SignOutAsync()
    {
        IsSigningOut = true;
        try
        {
            if (!await sync.FlushAndClearAsync(discardUnsynced: false, CancellationToken.None))
            {
                SignOutWarning = strings.Get("Settings.SignOutUnsynced", sync.Status.PendingChanges);
                return;
            }

            SignOutWarning = string.Empty;
            await auth.SignOutAsync();
        }
        finally
        {
            IsSigningOut = false;
        }
    }

    /// <summary>
    /// Signs out and drops the changes that never reached the server. Its danger row asks first
    /// (<see cref="SignOutAnywayLost"/>, Cancel focused), so this runs only after the owner chose it.
    /// </summary>
    [RelayCommand(CanExecute = nameof(CanSignOut))]
    private async Task SignOutAnywayAsync()
    {
        IsSigningOut = true;
        try
        {
            await sync.FlushAndClearAsync(discardUnsynced: true, CancellationToken.None);
            SignOutWarning = string.Empty;
            await auth.SignOutAsync();
        }
        finally
        {
            IsSigningOut = false;
        }
    }

    private void ShowSession(AuthSession session) =>
        Email = session is AuthSession.SignedIn signedIn ? signedIn.Email : string.Empty;

    // ---------------------------------------------------------------- Updates and about

    public bool IsUpdateIdle => !IsUpdating;

    public bool CanInstall => AvailableUpdate is not null;

    /// <summary>What the last check or install said, in place of the Check row's hint; null shows the hint.</summary>
    public string? UpdateResult => UpdateStatus.Length > 0 ? UpdateStatus : null;

    /// <summary>
    /// The latest release's page, where the owner can always download GoalMaker themselves; shown
    /// whenever the build has an update channel, and most useful when a check fails.
    /// </summary>
    public bool HasReleasesPage => releasesPage is not null;

    /// <summary>The accent row's words, "Version X is available.", shown while an update waits.</summary>
    public string AvailableText =>
        AvailableUpdate is null ? string.Empty : strings.Get("Settings.Update.Available", AvailableUpdate.Manifest.Version);

    public string InstallText =>
        AvailableUpdate is null ? string.Empty : strings.Get("Settings.InstallUpdate", AvailableUpdate.Manifest.Version);

    /// <summary>When a check last reached the update channel, the owner's or the quiet daily one.</summary>
    public string LastCheckedText => updateChecks.LastChecked is { } last
        ? strings.Get("Settings.Update.LastChecked", last.ToLocalTime().ToString("g", CultureInfo.CurrentCulture))
        : string.Empty;

    public bool HasLastChecked => HasReleasesPage && updateChecks.LastChecked is not null;

    /// <summary>The Check row's hint: when a check last got through, or that GoalMaker checks daily.</summary>
    public string UpdateCheckHint => HasLastChecked ? LastCheckedText : strings.Get("Settings.UpdateCheckHint");

    public string VersionValue => appInfo.Version;

    public string BackendValue => appInfo.Backend.Url;

    public bool IsDevBuild => appInfo.IsDevBuild;

    [RelayCommand(CanExecute = nameof(IsUpdateIdle))]
    private async Task CheckForUpdatesAsync()
    {
        IsUpdating = true;
        IsChecking = true;
        AvailableUpdate = null;
        UpdateStatus = string.Empty;
        try
        {
            var result = await updateChecks.CheckNowAsync(CancellationToken.None);
            // A failed check says why and leaves an update an earlier check found.
            AvailableUpdate = updates.Waiting;
            UpdateStatusKind = result is UpdateCheckResult.Untrusted or UpdateCheckResult.Failed ? SettingsRowResult.Error : SettingsRowResult.Success;
            UpdateStatus = result switch
            {
                UpdateCheckResult.NotConfigured => strings.Get("Settings.Update.NotConfigured"),
                UpdateCheckResult.DevelopmentBuild => strings.Get("Settings.Update.DevBuild"),
                UpdateCheckResult.UpToDate upToDate => strings.Get("Settings.Update.UpToDate", upToDate.Latest),
                // The accent row says it, with the install button beside it.
                UpdateCheckResult.Available => string.Empty,
                UpdateCheckResult.Untrusted => strings.Get("Settings.Update.Untrusted"),
                UpdateCheckResult.Failed failed => strings.Get("Settings.Update.Failed", failed.Detail),
                _ => string.Empty,
            };
        }
        finally
        {
            IsChecking = false;
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
        UpdateStatusKind = SettingsRowResult.Success;
        var progress = new Progress<double>(value =>
        {
            DownloadProgress = value;
            UpdateStatus = strings.Get("Settings.Update.Downloading", value);
        });
        try
        {
            var result = await updates.InstallAsync(update, progress, CancellationToken.None);
            UpdateStatusKind = result is InstallResult.InstallerStarted ? SettingsRowResult.Success : SettingsRowResult.Error;
            UpdateStatus = result switch
            {
                InstallResult.InstallerStarted => strings.Get("Settings.Update.Started"),
                InstallResult.DownloadCorrupted => strings.Get("Settings.Update.Corrupted"),
                InstallResult.Failed failed => strings.Get("Settings.Update.Failed", failed.Detail),
                _ => string.Empty,
            };
            // A failed install leaves the update waiting, so Install can be tried again.
            if (result is InstallResult.InstallerStarted)
            {
                AvailableUpdate = null;
            }
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

    // ---------------------------------------------------------------- Developer

    /// <summary>The backend address must be http or https with a host; a bad one is never saved.</summary>
    public Func<string, string?> ValidateBackendUrl => text => SettingsFieldRules.BackendUrl(text) ? null : strings.Get("Settings.BackendUrlInvalid");

    public Func<string, string?> ValidateBackendKey => text => text.Trim().Length > 0 ? null : strings.Get("Settings.BackendKeyInvalid");

    /// <summary>Saves the backend the fields hold and restarts; a bad address or an empty key saves nothing.</summary>
    [RelayCommand]
    private void SaveBackend()
    {
        if (!appInfo.IsDevBuild || ValidateBackendUrl(BackendUrlDraft) is not null || ValidateBackendKey(BackendKeyDraft) is not null)
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

    // ---------------------------------------------------------------- Saving

    partial void OnAppearanceChanged(Appearance value)
    {
        settings.Appearance = value;
        applyAppearance(value);
        if (isDark() != themesDark)
        {
            BuildThemes();
            OnPropertyChanged(nameof(Themes));
            OnPropertyChanged(nameof(SelectedTheme));
        }
    }

    private void BuildThemes()
    {
        themesDark = isDark();
        themes = [.. design.Themes.Select(theme => new ThemeOptionViewModel(theme, themesDark))];
    }

    private static double Half(TimeOnly time) => (time.Hour * 2) + (time.Minute >= 30 ? 1 : 0);

    private static TimeOnly FromHalf(double half)
    {
        var whole = (int)Math.Clamp(Math.Round(half), 0, LastHalfHour);
        return new TimeOnly(whole / 2, whole % 2 * 30);
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
        OnPropertyChanged(nameof(PlanReminderHalf));
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
        OnPropertyChanged(nameof(WeeklyReviewHalf));
        OnPropertyChanged(nameof(WeeklyReviewSummary));
        quietHoursChanged();
    }

    private void SetWantsReady(TimeOnly? time)
    {
        if (time == settings.WantsReadyReminder)
        {
            return;
        }

        settings.WantsReadyReminder = time;
        OnPropertyChanged(nameof(WantsReadyOn));
        OnPropertyChanged(nameof(WantsReadyHalf));
        OnPropertyChanged(nameof(WantsReadySummary));
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
        OnPropertyChanged(nameof(MonthlyReviewHalf));
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
        OnPropertyChanged(nameof(QuietHoursStartText));
        OnPropertyChanged(nameof(QuietHoursEndText));
        OnPropertyChanged(nameof(QuietHoursSummary));
        OnPropertyChanged(nameof(IsQuietHoursOn));
        TurnOffQuietHoursCommand.NotifyCanExecuteChanged();
        quietHoursChanged();
    }
}

using System.Text.Json;
using System.Text.Json.Serialization;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Navigation;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.Infrastructure.Settings;

/// <summary><see cref="ISettingsStore"/> in a small JSON file next to the session. Not synced.</summary>
public sealed class JsonSettingsStore : ISettingsStore
{
    private static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() },
    };
    private readonly string path;
    private SettingsDocument document;

    public JsonSettingsStore(string path)
    {
        this.path = path;
        document = Load(path);
    }

    public Appearance Appearance
    {
        get => new(document.ThemeId, document.ThemeMode, document.PureBlack, document.ReduceMotion, document.CompletionSound);
        set => Save(document with
        {
            ThemeId = value.ThemeId,
            ThemeMode = value.Mode,
            PureBlack = value.PureBlack,
            ReduceMotion = value.ReduceMotion,
            CompletionSound = value.CompletionSound,
        });
    }

    public int DayStartHour
    {
        get => Math.Clamp(document.DayStartHour, 0, PlanningDay.LatestStartHour);
        set => Save(document with { DayStartHour = Math.Clamp(value, 0, PlanningDay.LatestStartHour) });
    }

    public QuietHours QuietHours
    {
        get => new(document.QuietHoursStart, document.QuietHoursEnd);
        set => Save(document with { QuietHoursStart = value.Start, QuietHoursEnd = value.End });
    }

    public TimeOnly? PlanTomorrowReminder
    {
        get => document.PlanTomorrowReminder;
        set => Save(document with { PlanTomorrowReminder = value });
    }

    public TimeOnly? WeeklyReviewReminder
    {
        get => document.WeeklyReviewReminder;
        set => Save(document with { WeeklyReviewReminder = value });
    }

    public int WeeklyReviewWeekday
    {
        get => document.WeeklyReviewWeekday;
        set => Save(document with { WeeklyReviewWeekday = Math.Clamp(value, 1, 7) });
    }

    public TimeOnly? WantsReadyReminder
    {
        get => document.WantsReadyReminder;
        set => Save(document with { WantsReadyReminder = value });
    }

    public WhyFrequency WhyReminder
    {
        get => WhyFrequencies.Of(document.WhyReminder);
        set => Save(document with { WhyReminder = WhyFrequencies.Key(value) });
    }

    public TimeOnly? MonthlyReviewReminder
    {
        get => document.MonthlyReviewReminder;
        set => Save(document with { MonthlyReviewReminder = value });
    }

    public DateTimeOffset? RemindedUntil
    {
        get => document.RemindedUntil;
        set => Save(document with { RemindedUntil = value });
    }

    public DateTimeOffset? SignedInAt
    {
        get => document.SignedInAt;
        set => Save(document with { SignedInAt = value });
    }

    public string? QuickAddHotkey
    {
        get => document.QuickAddHotkey;
        set => Save(document with { QuickAddHotkey = value });
    }

    public ComposerMode ComposerMode
    {
        get => document.ComposerMode;
        set => Save(document with { ComposerMode = value });
    }

    public bool NavigationCollapsed
    {
        get => document.NavigationCollapsed;
        set => Save(document with { NavigationCollapsed = value });
    }

    public BackendEnvironment? BackendOverride
    {
        get
        {
            if (document.BackendUrl is not { } url || document.BackendKey is not { } key)
            {
                return null;
            }

            var environment = new BackendEnvironment(url, key);
            return environment.IsConfigured ? environment : null;
        }

        set => Save(document with { BackendUrl = value?.Url.Trim(), BackendKey = value?.PublishableKey.Trim() });
    }

    public WindowPlacement? MainWindowPlacement
    {
        get => document.MainWindowPlacement;
        set => Save(document with { MainWindowPlacement = value });
    }

    public IReadOnlyList<string> PinnedPlaces
    {
        get => PlaceRules.Stored(document.PinnedPlaces, DeviceKind.Pc);
        set => Save(document with { PinnedPlaces = [.. PlaceRules.Stored(value, DeviceKind.Pc)] });
    }

    /// <summary>Only the four columns count, each once, so a file edited by hand can't fold something unknown.</summary>
    public IReadOnlyList<string> FoldedBoardColumns
    {
        get => Known(document.FoldedBoardColumns ?? []);
        set => Save(document with { FoldedBoardColumns = [.. Known(value)] });
    }

    /// <summary>A value the file doesn't know reads as the ladder.</summary>
    public GoalsView GoalsView
    {
        get => Enum.IsDefined(document.GoalsView) ? document.GoalsView : GoalsView.Ladder;
        set => Save(document with { GoalsView = value });
    }

    public int? NewYearDismissed
    {
        get => document.NewYearDismissed;
        set => Save(document with { NewYearDismissed = value });
    }

    public IReadOnlyDictionary<string, MiniWindowState> MiniWindows
    {
        get => document.MiniWindows ?? [];
        set => Save(document with { MiniWindows = new Dictionary<string, MiniWindowState>(value, StringComparer.Ordinal) });
    }

    public string? WeeklyBackupFolder
    {
        get => document.WeeklyBackupFolder;
        set => Save(document with { WeeklyBackupFolder = string.IsNullOrWhiteSpace(value) ? null : value.Trim() });
    }

    public DateTimeOffset? WeeklyBackupWritten
    {
        get => document.WeeklyBackupWritten;
        set => Save(document with { WeeklyBackupWritten = value });
    }

    public DateTimeOffset? UpdatesCheckedAt
    {
        get => document.UpdatesCheckedAt;
        set => Save(document with { UpdatesCheckedAt = value });
    }

    public string? UpdateFound
    {
        get => document.UpdateFound;
        set => Save(document with { UpdateFound = value });
    }

    public bool TallyOn
    {
        get => document.TallyOn;
        set => Save(document with { TallyOn = value });
    }

    public string DeviceId
    {
        get
        {
            if (document.DeviceId is not { Length: > 0 } id)
            {
                id = Guid.NewGuid().ToString();
                Save(document with { DeviceId = id });
            }

            return id;
        }
    }

    private static List<string> Known(IEnumerable<string> columns) =>
        [.. ProjectRules.Columns.Where(columns.Contains)];

    private static SettingsDocument Load(string path)
    {
        try
        {
            return File.Exists(path)
                ? JsonSerializer.Deserialize<SettingsDocument>(File.ReadAllText(path), Options) ?? new SettingsDocument()
                : new SettingsDocument();
        }
        catch (JsonException)
        {
            return new SettingsDocument();
        }
    }

    private void Save(SettingsDocument updated)
    {
        document = updated;
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary = path + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(document, Options));
        File.Move(temporary, path, overwrite: true);
    }
}

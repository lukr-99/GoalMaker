using System.Text.Json;
using System.Text.Json.Serialization;
using GoalMaker.Core.Backend;
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

    public DateTimeOffset? RemindedUntil
    {
        get => document.RemindedUntil;
        set => Save(document with { RemindedUntil = value });
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

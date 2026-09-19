using GoalMaker.Core.Settings;
using GoalMaker.Infrastructure.Settings;

namespace GoalMaker.Core.Tests.Settings;

public sealed class JsonSettingsStoreTests : IDisposable
{
    private readonly string folder = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"));

    private string SettingsFile => Path.Combine(folder, "settings.json");

    public void Dispose()
    {
        if (Directory.Exists(folder))
        {
            Directory.Delete(folder, recursive: true);
        }
    }

    [Fact]
    public void AFreshInstallUsesTheDefaultAppearance() =>
        Assert.Equal(Appearance.Default, new JsonSettingsStore(SettingsFile).Appearance);

    [Fact]
    public void TheAppearanceSurvivesARestart()
    {
        var chosen = new Appearance("night", ThemeMode.Dark, PureBlack: true, ReduceMotion.On, CompletionSound: true);

        var store = new JsonSettingsStore(SettingsFile);
        store.Appearance = chosen;

        Assert.Equal(chosen, new JsonSettingsStore(SettingsFile).Appearance);
    }

    [Fact]
    public void AFileFromBeforeThemesKeepsItsLightOrDarkChoice()
    {
        Directory.CreateDirectory(folder);
        File.WriteAllText(SettingsFile, """{ "Version": 1, "ThemeMode": "Light" }""");

        var appearance = new JsonSettingsStore(SettingsFile).Appearance;

        Assert.Equal(ThemeMode.Light, appearance.Mode);
        Assert.Null(appearance.ThemeId);
        Assert.False(appearance.PureBlack);
        Assert.Equal(ReduceMotion.System, appearance.ReduceMotion);
    }

    [Fact]
    public void OtherSettingsAreKeptWhenTheAppearanceChanges()
    {
        var store = new JsonSettingsStore(SettingsFile) { MainWindowPlacement = new WindowPlacement(10, 20, 800, 600, false) };

        store.Appearance = store.Appearance with { ThemeId = "sunrise" };

        Assert.Equal(new WindowPlacement(10, 20, 800, 600, false), new JsonSettingsStore(SettingsFile).MainWindowPlacement);
    }

    [Fact]
    public void TheEveningReminderIsAt2000UntilMovedOrSwitchedOff()
    {
        Directory.CreateDirectory(folder);
        File.WriteAllText(SettingsFile, """{ "Version": 1 }""");
        var store = new JsonSettingsStore(SettingsFile);
        Assert.Equal(new TimeOnly(20, 0), store.PlanTomorrowReminder);

        store.PlanTomorrowReminder = new TimeOnly(21, 30);
        Assert.Equal(new TimeOnly(21, 30), new JsonSettingsStore(SettingsFile).PlanTomorrowReminder);

        store.PlanTomorrowReminder = null;
        Assert.Null(new JsonSettingsStore(SettingsFile).PlanTomorrowReminder);
    }
}

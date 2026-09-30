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
    public void ThePinsStartAsTheDefaultsKeepTheirOrderAndDropWhatIsGone()
    {
        var store = new JsonSettingsStore(SettingsFile);
        Assert.Equal(["today", "tomorrow", "inbox", "projects"], store.PinnedPlaces);

        store.PinnedPlaces = ["stats", "today", "habits", "goals", "inbox"];

        Assert.Equal(["stats", "today", "habits", "goals", "inbox"], new JsonSettingsStore(SettingsFile).PinnedPlaces);

        File.WriteAllText(SettingsFile, """{ "Version": 1, "PinnedPlaces": ["focus", "stats", "stats"] }""");
        Assert.Equal(["stats"], new JsonSettingsStore(SettingsFile).PinnedPlaces);
    }

    [Fact]
    public void FoldedColumnsAreKeptInBoardOrderAndOnlyTheKnownOnes()
    {
        var store = new JsonSettingsStore(SettingsFile);
        Assert.Empty(store.FoldedBoardColumns);

        store.FoldedBoardColumns = ["done", "backlog"];

        Assert.Equal(["backlog", "done"], new JsonSettingsStore(SettingsFile).FoldedBoardColumns);

        File.WriteAllText(SettingsFile, """{ "Version": 1, "FoldedBoardColumns": ["done", "later", "done"] }""");
        Assert.Equal(["done"], new JsonSettingsStore(SettingsFile).FoldedBoardColumns);
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
    public void TheDeviceIdIsMadeOnceAndKept()
    {
        var id = new JsonSettingsStore(SettingsFile).DeviceId;

        Assert.True(Guid.TryParse(id, out _));
        Assert.Equal(id, new JsonSettingsStore(SettingsFile).DeviceId);
        Assert.NotEqual(id, new JsonSettingsStore(Path.Combine(folder, "other.json")).DeviceId);
    }

    [Fact]
    public void TheComposerAddsTasksUntilChatIsChosenAndThenRemembersIt()
    {
        Directory.CreateDirectory(folder);
        File.WriteAllText(SettingsFile, """{ "Version": 1 }""");
        var store = new JsonSettingsStore(SettingsFile);
        Assert.Equal(ComposerMode.QuickAdd, store.ComposerMode);

        store.ComposerMode = ComposerMode.Chat;

        Assert.Equal(ComposerMode.Chat, new JsonSettingsStore(SettingsFile).ComposerMode);
    }

    [Fact]
    public void TallyIsOffUntilTurnedOn()
    {
        Directory.CreateDirectory(folder);
        File.WriteAllText(SettingsFile, """{ "Version": 1 }""");
        var store = new JsonSettingsStore(SettingsFile);
        Assert.False(store.TallyOn);

        store.TallyOn = true;

        Assert.True(new JsonSettingsStore(SettingsFile).TallyOn);
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

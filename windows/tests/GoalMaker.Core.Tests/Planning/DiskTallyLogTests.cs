using GoalMaker.Infrastructure.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Tally's raw log on disk (M8-12): one file a day, read back in order, removed by day.</summary>
public sealed class DiskTallyLogTests : IDisposable
{
    private readonly string folder = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"), "tally");

    public void Dispose()
    {
        var root = Path.GetDirectoryName(folder)!;
        if (Directory.Exists(root))
        {
            Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public void ADaysLinesComeBackFromItsOwnFile()
    {
        var log = new DiskTallyLog(folder);
        Assert.Empty(log.Days());

        log.Append(new DateOnly(2026, 9, 30), "{\"app\":\"code.exe\"}");
        log.Append(new DateOnly(2026, 9, 30), "{\"app\":\"chrome.exe\"}");
        log.Append(new DateOnly(2026, 9, 29), "{\"app\":\"steam.exe\"}");
        File.WriteAllText(Path.Combine(folder, "notes.jsonl"), "not a day");

        Assert.Equal([new DateOnly(2026, 9, 29), new DateOnly(2026, 9, 30)], log.Days());
        Assert.Equal(["{\"app\":\"code.exe\"}", "{\"app\":\"chrome.exe\"}"], log.Read(new DateOnly(2026, 9, 30)));
        Assert.True(File.Exists(Path.Combine(folder, "2026-09-30.jsonl")));

        log.Remove(new DateOnly(2026, 9, 29));

        Assert.Equal([new DateOnly(2026, 9, 30)], log.Days());
        Assert.Empty(log.Read(new DateOnly(2026, 9, 29)));
    }
}

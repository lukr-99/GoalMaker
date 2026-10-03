using System.Text.Json;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using GoalMaker.Infrastructure.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>
/// Tally's tracker on the PC (M8-12, docs/tally.md) with a fake foreground source, log and clock:
/// switches, titles, idle, lock and sleep, the Video exception, the project from an editor's title,
/// the 30-day log and the day totals across the 04:00 rollover.
/// </summary>
public sealed class TallyTrackerTests : IDisposable
{
    private const string VsCode = "code.exe";
    private const string Chrome = "chrome.exe";
    private const string GoalMakerTitle = "TallyTracker.cs - GoalMaker - Visual Studio Code";
    private static readonly DateOnly Today = new(2026, 9, 30);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 30, 10, 0, 0, TimeSpan.Zero));
    private readonly FakeSource source;
    private readonly FakeLog log = new();
    private readonly TallyList tally;
    private readonly string project;
    private readonly TallyTracker tracker;
    private string? owner = TestReplica.Owner;

    public TallyTrackerTests()
    {
        source = new FakeSource(time);
        var rows = new NewRows(test.Catalog, () => owner, time);
        tally = new TallyList(test.Replica, rows, () => "D1E57000-0000-4000-8000-00000000AAAA", () => { });
        var projects = new ProjectList(test.Replica, rows, () => { });
        project = projects.Add(new ProjectDraft("GoalMaker") { LocalFolder = @"C:\Users\owner\Code\GoalMaker" })!.Id;
        tracker = new TallyTracker(source, log, tally, ContractResources.TallyDefaults().Rules, projects.All, () => 4, time);
    }

    public void Dispose()
    {
        tracker.Dispose();
        test.Dispose();
    }

    [Fact]
    public void ASwitchEndsOneStretchAndStartsTheNext()
    {
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        Pass(TimeSpan.FromMinutes(2));

        source.Switch(new ForegroundApp(Chrome, "Inbox - Gmail - Google Chrome"));
        Pass(TimeSpan.FromMinutes(3));
        tracker.Stop();

        Assert.Equal(
            [(At(10, 0), At(10, 2), VsCode, "coding"), (At(10, 2), At(10, 5), Chrome, "email")],
            Entries().Select(entry => (entry.Start, entry.End, entry.App, entry.Category)));
    }

    [Fact]
    public void ANewTitleIsNoticedAtTheNextLook()
    {
        source.Window = new ForegroundApp(Chrome, "Rust - Wikipedia - Google Chrome");
        tracker.Start();
        Wait(TimeSpan.FromSeconds(65));

        // A tab changed without a window switch; the look at 10:01:15 sees it.
        source.Window = new ForegroundApp(Chrome, "Rust in 100 seconds - YouTube - Google Chrome");
        Wait(TimeSpan.FromSeconds(55));
        tracker.Stop();

        Assert.Equal(
            [(At(10, 0), At(10, 1, 15), "reading"), (At(10, 1, 15), At(10, 2), "video")],
            Entries().Select(entry => (entry.Start, entry.End, entry.Category)));
    }

    [Fact]
    public void FiveMinutesWithoutInputStopTheClockWhereTheyEnd()
    {
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        Wait(TimeSpan.FromMinutes(10));

        source.Touch();
        Wait(TimeSpan.FromMinutes(1));
        tracker.Stop();

        Assert.Equal(
            [(At(10, 0), At(10, 5)), (At(10, 10, 15), At(10, 11))],
            Entries().Select(entry => (entry.Start, entry.End)));
    }

    [Fact]
    public void AVideoKeepsCountingWithoutInput()
    {
        source.Window = new ForegroundApp(Chrome, "A long talk - YouTube - Google Chrome");
        tracker.Start();
        Wait(TimeSpan.FromMinutes(20));
        tracker.Stop();

        var entry = Assert.Single(Entries());
        Assert.Equal((At(10, 0), At(10, 20), TallyRules.Video), (entry.Start, entry.End, entry.Category));
    }

    [Fact]
    public void LockAndSleepStopTheClockUntilTheOwnerIsBack()
    {
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        Pass(TimeSpan.FromMinutes(2));

        source.Lock(true);
        Pass(TimeSpan.FromMinutes(10));
        source.Lock(false);
        Pass(TimeSpan.FromMinutes(1));
        source.Sleep(true);
        Pass(TimeSpan.FromMinutes(30));
        source.Sleep(false);
        Pass(TimeSpan.FromMinutes(1));
        tracker.Stop();

        Assert.Equal(
            [(At(10, 0), At(10, 2)), (At(10, 12), At(10, 13)), (At(10, 43), At(10, 44))],
            Entries().Select(entry => (entry.Start, entry.End)));
    }

    [Fact]
    public void ALookLongOverdueCountsNothingInBetween()
    {
        source.Window = new ForegroundApp(Chrome, "A long talk - YouTube - Google Chrome");
        tracker.Start();
        Pass(TimeSpan.FromMinutes(1));

        // The PC slept without a word; the next look comes half an hour late.
        time.Advance(TimeSpan.FromMinutes(30));
        Pass(TimeSpan.FromMinutes(1));
        tracker.Stop();

        var entries = Entries();
        Assert.Equal(2, entries.Count);
        Assert.Equal((At(10, 0), At(10, 1)), (entries[0].Start, entries[0].End));
        Assert.True(entries[1].Start >= At(10, 31));
    }

    [Fact]
    public void TimeInVsCodeCountsTowardTheProjectItsTitleNames()
    {
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        Pass(TimeSpan.FromMinutes(30));
        source.Switch(new ForegroundApp(VsCode, "notes.md - Scratch - Visual Studio Code"));
        Pass(TimeSpan.FromMinutes(10));

        tracker.Flush();

        Assert.Equal(
            [("coding", null, 10), ("coding", project, 30)],
            tally.Days(Today, Today).Select(day => (day.Category, day.ProjectId, day.Minutes)));
        Assert.Equal(project, Entries()[0].Project);
    }

    [Fact]
    public void OnlyTheLastThirtyDaysOfLogAreKept()
    {
        for (var day = Today.AddDays(-40); day <= Today; day = day.AddDays(1))
        {
            log.Append(day, "{}");
        }

        tracker.Start();

        Assert.Equal(TallyTracker.KeepDays, log.Days().Count);
        Assert.Equal(new DateOnly(2026, 9, 1), log.Days()[0]);
        Assert.Equal(Today, log.Days()[^1]);
    }

    [Fact]
    public void AStretchOverTheRolloverCountsOnBothPlanningDays()
    {
        time.SetUtcNow(new DateTimeOffset(2026, 10, 1, 3, 50, 0, TimeSpan.Zero));
        source.Touch();
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        Pass(TimeSpan.FromMinutes(20));

        // The sync timer writes the open stretch so far; it goes on from here.
        tracker.Flush();

        Assert.Equal([(Today, 10), (Today.AddDays(1), 10)], tally.Days(Today, Today.AddDays(1)).Select(day => (day.Day, day.Minutes)));
        Pass(TimeSpan.FromMinutes(5));
        tracker.Flush();
        Assert.Equal(15, tally.Days(Today.AddDays(1), Today.AddDays(1)).Single().Minutes);
    }

    [Fact]
    public void ADayWaitsUntilSomeoneIsSignedIn()
    {
        owner = null;
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        Pass(TimeSpan.FromMinutes(5));
        tracker.Stop();
        Assert.Empty(tally.Days(Today, Today));

        owner = TestReplica.Owner;
        tracker.Flush();

        Assert.Equal(5, tally.Days(Today, Today).Single().Minutes);
    }

    [Fact]
    public void ItLooksOnlyEveryFifteenSeconds()
    {
        source.Window = new ForegroundApp(VsCode, GoalMakerTitle);
        tracker.Start();
        time.Advance(TimeSpan.FromMinutes(1));

        Assert.Equal(5, source.Looks);
        tracker.Stop();
        time.Advance(TimeSpan.FromMinutes(1));
        Assert.Equal(5, source.Looks);
        Assert.False(source.Listening);
    }

    [Fact]
    public void ARuleThatArrivesWhileItRunsSortsTheNextWindow()
    {
        source.Window = new ForegroundApp(Chrome, "Puzzle 1 - lichess.org - Google Chrome");
        tracker.Start();
        Pass(TimeSpan.FromMinutes(5));

        // Added in the Tally place, here or on the phone; the replica holds it either way.
        tally.AddRule(new TallyRule(TallyRules.Title, "lichess", TallyRules.Any, "games"));
        source.Switch(new ForegroundApp(Chrome, "Puzzle 2 - lichess.org - Google Chrome"));
        Pass(TimeSpan.FromMinutes(5));
        tracker.Stop();

        var entries = Entries();
        Assert.NotEqual("games", entries[0].Category);
        Assert.Equal("games", entries[1].Category);
    }

    [Fact]
    public void ThePageReadsThisPcsOwnStretchesTheOpenOneIncludedAndWritesNothing()
    {
        source.Window = new ForegroundApp(Chrome, "Lo-fi beats - YouTube - Google Chrome");
        tracker.Start();
        Pass(TimeSpan.FromMinutes(10));
        source.Switch(new ForegroundApp(VsCode, GoalMakerTitle));
        Pass(TimeSpan.FromMinutes(5));

        Assert.Equal(
            [(At(10, 0), At(10, 10), Chrome, "video"), (At(10, 10), At(10, 15), VsCode, "coding")],
            tracker.Stretches(Today, Today).Select(stretch => (stretch.Start, stretch.End, stretch.App, stretch.Category)));
        Assert.Equal("Lo-fi beats - YouTube - Google Chrome", tracker.Stretches(Today, Today)[0].Title);
        Assert.Empty(tracker.Stretches(Today.AddDays(1), Today.AddDays(1)));
        Assert.Empty(tally.Days(Today, Today));
    }

    [Fact]
    public void ARuleMadeOnThePageSortsTodayAgainAtOnceAndTheLogKeepsWhatItSaw()
    {
        source.Window = new ForegroundApp(Chrome, "Lo-fi beats - YouTube - Google Chrome");
        tracker.Start();
        Pass(TimeSpan.FromMinutes(10));
        tracker.Flush();
        Assert.Equal([("video", 10)], tally.Days(Today, Today).Select(day => (day.Category, day.Minutes)));

        tally.AddRule(new TallyRule(TallyRules.Title, "lo-fi", TallyRules.Windows, "music"));
        tracker.Recount();

        Assert.Equal([("music", 10)], tally.Days(Today, Today).Select(day => (day.Category, day.Minutes)));
        Assert.Equal(["music"], tracker.Stretches(Today, Today).Select(stretch => stretch.Category).Distinct());
        Assert.Equal("video", Entries()[0].Category);
    }

    private static DateTime At(int hour, int minute, int second = 0) => new(2026, 9, 30, hour, minute, second);

    // Time passes with the owner at the keyboard.
    private void Pass(TimeSpan span) => Step(span, touch: true);

    // Time passes with nobody touching anything.
    private void Wait(TimeSpan span) => Step(span, touch: false);

    // A few seconds at a time, so each look happens when it is due; one long jump reads as a PC that slept.
    private void Step(TimeSpan span, bool touch)
    {
        var end = time.GetUtcNow() + span;
        while (time.GetUtcNow() < end)
        {
            if (touch)
            {
                source.Touch();
            }

            time.Advance(TimeSpan.FromSeconds(5));
        }
    }

    private List<TallyEntry> Entries() =>
        [.. log.Days().SelectMany(log.Read).Select(line => JsonSerializer.Deserialize<TallyEntry>(line, JsonSerializerOptions.Web)!)];

    private sealed class FakeSource(TimeProvider time) : IForegroundSource
    {
        private DateTimeOffset lastInput = time.GetUtcNow();

        public event EventHandler? Switched;

        public event EventHandler<bool>? LockChanged;

        public event EventHandler<bool>? SleepChanged;

        public ForegroundApp? Window { get; set; }

        public int Looks { get; private set; }

        public bool Listening { get; private set; }

        public ForegroundApp? Current()
        {
            Looks++;
            return Window;
        }

        public TimeSpan SinceInput() => time.GetUtcNow() - lastInput;

        public void Start() => Listening = true;

        public void Stop() => Listening = false;

        public void Touch() => lastInput = time.GetUtcNow();

        public void Switch(ForegroundApp window)
        {
            Window = window;
            Switched?.Invoke(this, EventArgs.Empty);
        }

        public void Lock(bool locked) => LockChanged?.Invoke(this, locked);

        public void Sleep(bool asleep) => SleepChanged?.Invoke(this, asleep);
    }

    private sealed class FakeLog : ITallyLog
    {
        private readonly SortedDictionary<DateOnly, List<string>> files = [];

        public IReadOnlyList<DateOnly> Days() => [.. files.Keys];

        public void Append(DateOnly day, string line)
        {
            if (!files.TryGetValue(day, out var lines))
            {
                files[day] = lines = [];
            }

            lines.Add(line);
        }

        public IReadOnlyList<string> Read(DateOnly day) => files.TryGetValue(day, out var lines) ? lines : [];

        public void Remove(DateOnly day) => files.Remove(day);
    }
}

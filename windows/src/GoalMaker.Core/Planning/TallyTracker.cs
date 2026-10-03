using System.Text.Json;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Tally on the PC (docs/tally.md, ADR 0013): follows the window in front, sorts its time with
/// <see cref="TallyRules"/> and keeps it in the raw log on this PC. The clock stops after five minutes
/// without input (not for video), on lock and on sleep. <see cref="Flush"/>, on the sync timer, turns
/// the log into this PC's daily totals, the only part that syncs, sorting it again with the rules as
/// they are then, so a new rule re-sorts the days still being written. <see cref="Stretches"/> reads
/// the log back for the Tally page's hours and apps.
/// </summary>
public sealed class TallyTracker : IDisposable
{
    /// <summary>How often the window's title and the idle time are read; browser tabs change without a switch.</summary>
    public static readonly TimeSpan Tick = TimeSpan.FromSeconds(15);

    /// <summary>How many days of raw log are kept, today included.</summary>
    public const int KeepDays = 30;

    // A tick this much later than due means the PC slept without saying so; that time is not counted.
    private static readonly TimeSpan Gap = TimeSpan.FromMinutes(2);
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    private readonly IForegroundSource source;
    private readonly ITallyLog log;
    private readonly TallyList tally;
    private readonly IReadOnlyList<TallyRule> defaults;
    private readonly Func<IReadOnlyList<ProjectItem>> projects;
    private readonly Func<int> dayStartHour;
    private readonly TimeProvider time;
    private readonly Lock gate = new();
    private readonly SortedSet<DateOnly> touched = [];
    private ITimer? timer;
    private ForegroundApp? window;
    private TallySort? sort;
    private DateTime? since;
    private DateTime lastLook;
    private bool locked;
    private bool asleep;

    public TallyTracker(
        IForegroundSource source,
        ITallyLog log,
        TallyList tally,
        IReadOnlyList<TallyRule> defaults,
        Func<IReadOnlyList<ProjectItem>> projects,
        Func<int> dayStartHour,
        TimeProvider time)
    {
        this.source = source;
        this.log = log;
        this.tally = tally;
        this.defaults = defaults;
        this.projects = projects;
        this.dayStartHour = dayStartHour;
        this.time = time;
    }

    public bool IsRunning
    {
        get
        {
            lock (gate)
            {
                return timer is not null;
            }
        }
    }

    /// <summary>Starts following the window in front. Today and yesterday are rewritten at the next flush, in case a crash left them behind.</summary>
    public void Start()
    {
        lock (gate)
        {
            if (timer is not null)
            {
                return;
            }

            var now = Now();
            var today = PlanningDay.Of(now, dayStartHour());
            touched.Add(today.AddDays(-1));
            touched.Add(today);
            Clean(now);
            locked = false;
            asleep = false;
            lastLook = now;
            timer = time.CreateTimer(_ => Look(), null, Tick, Tick);
        }

        // The source is called outside the lock, so its own events can never wait on it.
        source.Switched += OnSwitched;
        source.LockChanged += OnLockChanged;
        source.SleepChanged += OnSleepChanged;
        source.Start();
        Look();
    }

    /// <summary>Stops the clock, writes what was open and rewrites the touched days.</summary>
    public void Stop()
    {
        lock (gate)
        {
            if (timer is null)
            {
                return;
            }

            timer.Dispose();
            timer = null;
            Close(Now());
            window = null;
            sort = null;
        }

        source.Stop();
        source.Switched -= OnSwitched;
        source.LockChanged -= OnLockChanged;
        source.SleepChanged -= OnSleepChanged;
        Flush();
    }

    /// <summary>
    /// Writes the open stretch so far to the log, removes days older than <see cref="KeepDays"/>, and
    /// makes the totals of every day touched since the last flush this PC's rows for that day. A day
    /// that can't be written (nobody signed in) waits for the next flush.
    /// </summary>
    public void Flush()
    {
        lock (gate)
        {
            var now = Now();
            if (since is not null)
            {
                Close(now);
                since = now;
            }

            Clean(now);
            var hour = dayStartHour();
            var sorter = Sorter();
            foreach (var day in touched.ToList())
            {
                // A day's stretches are logged under the date they started on, so its neighbors hold some too.
                var intervals = Enumerable.Range(-1, 3)
                    .SelectMany(offset => Entries(day.AddDays(offset)))
                    .Select(entry => (entry, sort: sorter(entry.App, entry.Title)))
                    .Select(pair => new TallyInterval(pair.entry.Start, pair.entry.End, pair.sort.Category, pair.sort.Project));
                var totals = TallyRules.DayTotals(intervals, hour).Where(total => total.Day == day);
                if (tally.RewriteDay(day, totals))
                {
                    touched.Remove(day);
                }
            }
        }
    }

    /// <summary>
    /// Counts today again from the log with the rules as they are now, as the Tally page asks after the
    /// owner makes, changes or deletes a rule.
    /// </summary>
    public void Recount()
    {
        lock (gate)
        {
            touched.Add(PlanningDay.Of(Now(), dayStartHour()));
        }

        Flush();
    }

    /// <summary>
    /// What this PC's log says was in front from the start of the planning day <paramref name="from"/>
    /// to the end of <paramref name="to"/>, the window still open included, each sorted with the
    /// owner's rules and the shipped ones as they are now. Nothing is written.
    /// </summary>
    public IReadOnlyList<TallyStretch> Stretches(DateOnly from, DateOnly to)
    {
        lock (gate)
        {
            var sorter = Sorter();
            var hour = dayStartHour();
            var start = from.ToDateTime(new TimeOnly(hour, 0));
            var end = to.AddDays(1).ToDateTime(new TimeOnly(hour, 0));
            var read = Enumerable.Range(-1, to.DayNumber - from.DayNumber + 3)
                .SelectMany(offset => Entries(from.AddDays(offset)))
                .Select(entry => (entry.Start, entry.End, entry.App, entry.Title))
                .ToList();
            if (since is { } open && window is not null)
            {
                read.Add((open, Now(), window.App, window.Title));
            }

            return
            [
                .. read
                    .Where(entry => entry.End > start && entry.Start < end)
                    .Select(entry => new TallyStretch(entry.Start, entry.End, entry.App, entry.Title, sorter(entry.App, entry.Title).Category)),
            ];
        }
    }

    public void Dispose() => Stop();

    private DateTime Now() => time.GetLocalNow().DateTime;

    private void Look()
    {
        lock (gate)
        {
            if (timer is not null)
            {
                Observe(Now());
            }
        }
    }

    private void OnSwitched(object? sender, EventArgs e) => Look();

    private void OnLockChanged(object? sender, bool isLocked)
    {
        lock (gate)
        {
            locked = isLocked;
            if (timer is not null)
            {
                Observe(Now());
            }
        }
    }

    private void OnSleepChanged(object? sender, bool isAsleep)
    {
        lock (gate)
        {
            asleep = isAsleep;
            if (timer is not null)
            {
                Observe(Now());
            }
        }
    }

    // Looks at the window in front: another window or title ends the open stretch, and the clock runs
    // only while TallyRules.Counts says so. Idle ends the stretch five minutes after the last input.
    private void Observe(DateTime now)
    {
        if (since is not null && now - lastLook > Gap)
        {
            Close(lastLook);
        }

        lastLook = now;
        var current = source.Current();
        if (current != window)
        {
            Close(now);
            window = current;
            sort = current is null
                ? null
                : TallyRules.SortSample(new TallySample(TallyRules.Windows, current.App, current.Title), tally.Rules(), defaults, projects());
        }

        if (sort is null)
        {
            return;
        }

        var idle = source.SinceInput();
        if (TallyRules.Counts((int)Math.Min(idle.TotalSeconds, int.MaxValue), sort.Category, locked, asleep))
        {
            since ??= now;
        }
        else
        {
            Close(locked || asleep ? now : now - idle + TimeSpan.FromSeconds(TallyRules.IdleSeconds));
        }
    }

    // Ends the open stretch at the given time and logs it under the date it started on.
    private void Close(DateTime end)
    {
        if (since is not { } start || window is null || sort is null)
        {
            since = null;
            return;
        }

        since = null;
        if (end <= start)
        {
            return;
        }

        var entry = new TallyEntry(start, end, window.App, window.Title, sort.Category, sort.Project);
        log.Append(DateOnly.FromDateTime(start), JsonSerializer.Serialize(entry, Json));
        var hour = dayStartHour();
        for (var day = PlanningDay.Of(start, hour); day <= PlanningDay.Of(end, hour); day = day.AddDays(1))
        {
            touched.Add(day);
        }
    }

    // Sorts a window with the rules as they are now, each window once.
    private Func<string, string?, TallySort> Sorter()
    {
        var own = tally.Rules();
        var known = projects();
        var sorted = new Dictionary<(string App, string Title), TallySort>();
        return (app, title) =>
        {
            if (!sorted.TryGetValue((app, title ?? string.Empty), out var sort))
            {
                sort = TallyRules.SortSample(new TallySample(TallyRules.Windows, app, title), own, defaults, known);
                sorted[(app, title ?? string.Empty)] = sort;
            }

            return sort;
        };
    }

    private void Clean(DateTime now)
    {
        var oldest = DateOnly.FromDateTime(now).AddDays(1 - KeepDays);
        foreach (var day in log.Days().Where(day => day < oldest))
        {
            log.Remove(day);
        }
    }

    private IEnumerable<TallyEntry> Entries(DateOnly day)
    {
        foreach (var line in log.Read(day))
        {
            TallyEntry? entry;
            try
            {
                entry = JsonSerializer.Deserialize<TallyEntry>(line, Json);
            }
            catch (JsonException)
            {
                // A line cut short by a crash is skipped.
                continue;
            }

            // A line without its window can't be sorted, so it is skipped too.
            if (entry is { App: not null })
            {
                yield return entry;
            }
        }
    }
}

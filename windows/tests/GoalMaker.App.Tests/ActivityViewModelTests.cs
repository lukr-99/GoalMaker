using System.Text.Json.Nodes;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Activity;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.Tests;

/// <summary>The Activity page's list and Undo over a fake log (docs/activity.md).</summary>
public sealed class ActivityViewModelTests
{
    private readonly FakeLog log = new();
    private int syncs;

    [Fact]
    public async Task UndoIsOfferedOnEachRowsLatestChangeOnly()
    {
        log.Entries =
        [
            Entry(1, "tasks", "a", "create", "Call the bank", "claude"),
            Entry(2, "tasks", "a", "update", "Call the bank"),
            Entry(3, "tasks", "b", "create", "Buy milk"),
        ];
        var activity = Activity();

        await activity.RefreshAsync();

        Assert.Equal([3L, 2L, 1L], activity.Rows.Select(row => row.Entry.Id));
        Assert.Equal([true, true, false], activity.Rows.Select(row => row.Undoable));
        Assert.Equal("Activity.Completed(Activity.ActorOwner,Activity.Task(Call the bank))", activity.Rows[1].Sentence);
        Assert.Equal("Activity.Added(Activity.ActorClaude,Activity.Task(Call the bank))", activity.Rows[2].Sentence);
    }

    [Fact]
    public async Task AnUndoIsReportedSyncedAndTheListReadAgain()
    {
        log.Entries = [Entry(1, "tasks", "a", "create", "Oops", "claude")];
        var activity = Activity();
        await activity.RefreshAsync();

        await activity.Rows.Single().UndoCommand.ExecuteAsync(null);

        Assert.Equal([1L], log.Undone);
        Assert.Equal("Activity.UndoDone", activity.Message);
        Assert.Equal(1, syncs);
        Assert.False(activity.Rows.Single().Undoable);
    }

    [Fact]
    public async Task AChangeTheRowMovedOnFromIsExplainedAndNothingSyncs()
    {
        log.Entries = [Entry(1, "tasks", "a", "update", "Call the bank")];
        log.Outcome = UndoOutcome.ChangedSince;
        var activity = Activity();
        await activity.RefreshAsync();

        await activity.Rows.Single().UndoCommand.ExecuteAsync(null);

        Assert.Equal("Activity.UndoChanged", activity.Message);
        Assert.Equal(0, syncs);
    }

    [Fact]
    public async Task OfflineThePageSaysItNeedsAConnection()
    {
        log.Offline = true;
        var activity = Activity();

        await activity.RefreshAsync();

        Assert.True(activity.Unavailable);
        Assert.False(activity.ShowList);
    }

    private ActivityViewModel Activity() => new(log, new TestPlanner.FormatStrings(), () => syncs++);

    private static ActivityEntry Entry(long id, string entity, string entityId, string action, string title, string actor = "owner") => new(
        id,
        entity,
        entityId,
        action,
        actor,
        action == "create" ? null : new JsonObject { ["title"] = title, ["status"] = "open" },
        new JsonObject { ["title"] = title, ["status"] = action == "update" ? "done" : "open" },
        new DateTimeOffset(2026, 9, 19, 9, 0, 0, TimeSpan.Zero).AddSeconds(id),
        null);

    private sealed class FakeLog : IActivityLog
    {
        public IReadOnlyList<ActivityEntry> Entries { get; set; } = [];

        public UndoOutcome Outcome { get; set; } = UndoOutcome.Undone;

        public bool Offline { get; set; }

        public List<long> Undone { get; } = [];

        public Task<IReadOnlyList<ActivityEntry>> RecentAsync(int limit = 60, CancellationToken cancellationToken = default) =>
            Offline ? throw new RemoteUnavailableException("offline") : Task.FromResult(Entries);

        public Task<UndoOutcome> UndoAsync(long entryId, CancellationToken cancellationToken = default)
        {
            Undone.Add(entryId);
            if (Outcome == UndoOutcome.Undone)
            {
                Entries = [.. Entries.Select(entry => entry.Id == entryId ? entry with { UndoneAt = DateTimeOffset.UnixEpoch } : entry)];
            }

            return Task.FromResult(Outcome);
        }
    }
}

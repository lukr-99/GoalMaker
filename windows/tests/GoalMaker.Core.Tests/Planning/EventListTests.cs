using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Calendar events on a real replica (docs/calendar.md, M10-01).</summary>
public sealed class EventListTests : IDisposable
{
    private static readonly DateOnly Monday = new(2026, 10, 12);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 10, 5, 12, 0, 0, TimeSpan.Zero));
    private readonly EventList events;
    private int syncs;

    public EventListTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        events = new EventList(test.Replica, rows, () => syncs++);
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void ANewEventIsTrimmedAndAsksForASync()
    {
        var prague = events.Add(new EventDraft(" Prague ", Monday, Monday.AddDays(3), "  ", "travel"))!;

        Assert.Equal("Prague", prague.Title);
        Assert.Equal(Monday, prague.StartsOn);
        Assert.Equal(Monday.AddDays(3), prague.EndsOn);
        Assert.Equal(4, prague.Days);
        Assert.Null(prague.Notes);
        Assert.Equal("travel", prague.AreaId);
        Assert.Equal(ProjectRules.Owner, prague.MadeBy);
        Assert.Equal(1, syncs);
        Assert.Equal(prague, events.Get(prague.Id));
    }

    [Fact]
    public void WhatTheServerWouldRefuseIsNotStored()
    {
        Assert.Null(events.Add(new EventDraft("   ", Monday, Monday)));
        Assert.Null(events.Add(new EventDraft(new string('a', 201), Monday, Monday)));
        Assert.Null(events.Add(new EventDraft("Back to front", Monday, Monday.AddDays(-1))));
        Assert.Null(events.Add(new EventDraft("Too long", Monday, Monday.AddDays(367))));
        Assert.Null(events.Add(new EventDraft("Long notes", Monday, Monday, new string('n', 10001))));
        Assert.NotNull(events.Add(new EventDraft(new string('a', 200), Monday, Monday.AddDays(366), new string('n', 10000))));
        Assert.Single(events.All());
    }

    [Fact]
    public void AnEditChangesEveryFieldButNotIntoSomethingInvalid()
    {
        var trip = events.Add(new EventDraft("Prague", Monday, Monday.AddDays(3)))!;

        Assert.True(events.Update(trip.Id, new EventDraft("Prague and Brno", Monday.AddDays(7), Monday.AddDays(11), "Hotel", "travel")));
        Assert.False(events.Update(trip.Id, new EventDraft("Prague", Monday, Monday.AddDays(-2))));
        Assert.False(events.Update("missing", new EventDraft("Prague", Monday, Monday)));

        var stored = events.Get(trip.Id)!;
        Assert.Equal("Prague and Brno", stored.Title);
        Assert.Equal(Monday.AddDays(7), stored.StartsOn);
        Assert.Equal(Monday.AddDays(11), stored.EndsOn);
        Assert.Equal("Hotel", stored.Notes);
        Assert.Equal("travel", stored.AreaId);
    }

    [Fact]
    public void ARangeHoldsTheEventsThatTouchItInDayOrder()
    {
        var before = events.Add(new EventDraft("Before", Monday.AddDays(-5), Monday.AddDays(-1)))!;
        var across = events.Add(new EventDraft("Across", Monday.AddDays(-2), Monday.AddDays(1)))!;
        var inside = events.Add(new EventDraft("Inside", Monday.AddDays(3), Monday.AddDays(3)))!;
        var longer = events.Add(new EventDraft("Longer", Monday.AddDays(3), Monday.AddDays(9)))!;
        var gone = events.Add(new EventDraft("Gone", Monday, Monday))!;
        Assert.True(events.Delete(gone.Id));

        Assert.Equal([across.Id, longer.Id, inside.Id], events.Between(Monday, Monday.AddDays(6)).Select(item => item.Id));
        Assert.Equal([before.Id, across.Id, longer.Id, inside.Id], events.All().Select(item => item.Id));
    }

    [Fact]
    public void ADeleteIsUndoneByRestore()
    {
        var trip = events.Add(new EventDraft("Prague", Monday, Monday.AddDays(3)))!;
        var changes = 0;
        events.Changed += (_, _) => changes++;

        Assert.True(events.Delete(trip.Id));
        Assert.Null(events.Get(trip.Id));
        Assert.Empty(events.All());
        Assert.False(events.Delete(trip.Id));
        Assert.False(events.Update(trip.Id, new EventDraft("Prague", Monday, Monday)));

        Assert.True(events.Restore(trip.Id));
        Assert.False(events.Restore(trip.Id));
        Assert.Equal("Prague", events.Get(trip.Id)!.Title);
        Assert.Equal(2, changes);
    }
}

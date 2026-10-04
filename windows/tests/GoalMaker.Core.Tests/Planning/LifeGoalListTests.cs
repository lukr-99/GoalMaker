using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Life goals and their pictures' rows on a real replica (docs/life-goals.md, M9-01).</summary>
public sealed class LifeGoalListTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 10, 4, 12, 0, 0, TimeSpan.Zero));
    private readonly LifeGoalList lifeGoals;

    public LifeGoalListTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        lifeGoals = new LifeGoalList(test.Replica, rows, () => { });
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void ANewLifeGoalIsOpenLastAndNeedsAWhy()
    {
        var car = lifeGoals.Add(new LifeGoalDraft(" Own an Audi R8 ", " Proof that the work paid off ", new DateOnly(2036, 10, 4)))!;
        var run = lifeGoals.Add(new LifeGoalDraft("Run a marathon", "To know I can"))!;

        Assert.Equal("Own an Audi R8", car.Title);
        Assert.Equal("Proof that the work paid off", car.Why);
        Assert.Equal(new DateOnly(2036, 10, 4), car.By);
        Assert.Equal(LifeGoalRules.Open, car.Status);
        Assert.Equal(ProjectRules.Owner, car.MadeBy);
        Assert.True(run.Position > car.Position);
        Assert.Null(lifeGoals.Add(new LifeGoalDraft("Boat", "  ")));
        Assert.Equal(["Own an Audi R8", "Run a marathon"], lifeGoals.All().Select(goal => goal.Title));
    }

    [Fact]
    public void AchievedAndDroppedOnesGoBelowTheOpenOnesAndReopen()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var run = lifeGoals.Add(new LifeGoalDraft("Run a marathon", "To know I can"))!;
        var boat = lifeGoals.Add(new LifeGoalDraft("Sail to Greece", "The sea"))!;

        Assert.True(lifeGoals.Achieve(car.Id));
        time.Advance(TimeSpan.FromMinutes(1));
        Assert.True(lifeGoals.Drop(boat.Id));

        Assert.Equal([run.Id, boat.Id, car.Id], lifeGoals.All().Select(goal => goal.Id));
        Assert.NotNull(lifeGoals.Get(car.Id)!.ClosedAt);
        Assert.True(lifeGoals.Reopen(car.Id));
        Assert.Equal(LifeGoalRules.Open, lifeGoals.Get(car.Id)!.Status);
        Assert.Null(lifeGoals.Get(car.Id)!.ClosedAt);
    }

    [Fact]
    public void TheOwnerOrdersThem()
    {
        var a = lifeGoals.Add(new LifeGoalDraft("A", "a"))!;
        var b = lifeGoals.Add(new LifeGoalDraft("B", "b"))!;
        var c = lifeGoals.Add(new LifeGoalDraft("C", "c"))!;

        Assert.True(lifeGoals.Reorder([c.Id, a.Id, b.Id]));

        Assert.Equal(["C", "A", "B"], lifeGoals.All().Select(goal => goal.Title));
        Assert.False(lifeGoals.Reorder(["nope"]));
    }

    [Fact]
    public void PicturesKeepTheirOrderAndGoWithTheirLifeGoalAndComeBackWithIt()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var front = lifeGoals.AddPicture(car.Id, 1600, 900)!;
        var side = lifeGoals.AddPicture(car.Id, 1600, 1067)!;
        var old = lifeGoals.AddPicture(car.Id, 800, 600)!;
        Assert.True(lifeGoals.RemovePicture(old.Id));
        Assert.Null(lifeGoals.AddPicture(car.Id, 0, 900));
        Assert.Null(lifeGoals.AddPicture("missing", 1600, 900));

        Assert.True(lifeGoals.ReorderPictures(car.Id, [side.Id, front.Id]));
        Assert.Equal([side.Id, front.Id], lifeGoals.PicturesOf(car.Id).Select(picture => picture.Id));

        time.Advance(TimeSpan.FromMinutes(1));
        Assert.True(lifeGoals.Delete(car.Id));
        Assert.Null(lifeGoals.Get(car.Id));
        Assert.Empty(lifeGoals.AllPictures());

        Assert.True(lifeGoals.Restore(car.Id));
        Assert.Equal(car.Id, lifeGoals.Get(car.Id)!.Id);
        Assert.Equal([side.Id, front.Id], lifeGoals.PicturesOf(car.Id).Select(picture => picture.Id));
    }
}

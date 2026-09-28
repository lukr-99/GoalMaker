using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Wants and their thresholds on a real replica (docs/wants.md, M8-03).</summary>
public sealed class WantListTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 28, 12, 0, 0, TimeSpan.Zero));
    private readonly WantList wants;
    private DateOnly day = new(2026, 9, 28);

    public WantListTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        wants = new WantList(test.Replica, rows, () => { }, () => day);
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void ANewWantCoolsAsItsPriceSaysAndNeedsAReason()
    {
        var shoes = wants.Add(new WantDraft(" Trail shoes ", " The old ones have holes ", Price: 3400))!;

        Assert.Equal("Trail shoes", shoes.Title);
        Assert.Equal(30, shoes.CooldownDays);
        Assert.Equal(new DateOnly(2026, 10, 28), shoes.CoolsUntil);
        Assert.Equal(ProjectRules.Owner, shoes.MadeBy);
        Assert.Null(wants.Add(new WantDraft("Boat", "  ")));
        Assert.Equal(["Trail shoes"], wants.All().Select(want => want.Title));
    }

    [Fact]
    public void TheOwnersOwnThresholdsGiveNewWantsTheirDaysAndLeaveTheOldOnes()
    {
        var before = wants.Add(new WantDraft("Lamp", "Dark desk", Price: 1500))!;
        Assert.True(wants.SetCooldowns(WantCooldowns.Default with { SmallUnder = 2000, SmallDays = 3 }));

        var after = wants.Add(new WantDraft("Mug", "Broke mine", Price: 1500))!;

        Assert.Equal(3, after.CooldownDays);
        Assert.Equal(30, wants.Get(before.Id)!.CooldownDays);
        Assert.Equal(2000, wants.Cooldowns().SmallUnder);
        Assert.True(wants.SetCooldowns(wants.Cooldowns() with { SmallDays = 5 }));
        Assert.Equal(5, wants.Cooldowns().SmallDays);
    }

    [Fact]
    public void ThresholdsThatDontHoldTogetherAreRefused()
    {
        Assert.False(wants.SetCooldowns(WantCooldowns.Default with { MediumUnder = 500 }));
        Assert.False(wants.SetCooldowns(WantCooldowns.Default with { LargeDays = 400 }));
        Assert.False(wants.SetCooldowns(WantCooldowns.Default with { Currency = "crowns" }));
        Assert.Equal(WantCooldowns.Default, wants.Cooldowns());
    }

    [Fact]
    public void APickedNumberOfDaysWinsAndAWantIsDecidedReopenedAndDeleted()
    {
        var kindle = wants.Add(new WantDraft("Kindle", "Reading at night", Price: 3290, PickedDays: 0))!;
        Assert.Equal(WantState.Ready, WantRules.State(kindle, day));

        Assert.True(wants.Decide(kindle.Id, WantRules.Dropped, " Library card works "));
        var dropped = wants.Get(kindle.Id)!;
        Assert.Equal(WantState.Decided, WantRules.State(dropped, day));
        Assert.Equal("Library card works", dropped.DecisionNote);
        Assert.False(wants.Decide(kindle.Id, "maybe"));

        Assert.True(wants.Reopen(kindle.Id));
        Assert.Null(wants.Get(kindle.Id)!.Decision);

        Assert.True(wants.Delete(kindle.Id));
        Assert.Null(wants.Get(kindle.Id));
        Assert.False(wants.Decide(kindle.Id, WantRules.Bought));
    }

    [Fact]
    public void EditingAWantKeepsItsCooldown()
    {
        var desk = wants.Add(new WantDraft("Desk", "Back pain", Price: 12900))!;
        day = new DateOnly(2026, 10, 10);

        Assert.True(wants.Update(desk.Id, new WantDraft("Standing desk", "Back pain", Price: 900)));

        var edited = wants.Get(desk.Id)!;
        Assert.Equal("Standing desk", edited.Title);
        Assert.Equal(90, edited.CooldownDays);
        Assert.Equal(new DateOnly(2026, 12, 27), edited.CoolsUntil);
    }
}

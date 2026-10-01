using GoalMaker.App.Startup;

namespace GoalMaker.App.Tests;

/// <summary>
/// GoalMaker keeps its own single-instance lock, because a second launch hands its switches and
/// goalmaker:// links to the running app, and the tray kit's lock only knocks.
/// </summary>
public sealed class SingleInstanceTests
{
    [Fact]
    public void OnlyTheFirstLaunchGetsTheLock()
    {
        var name = "GoalMaker.Tests." + Guid.NewGuid().ToString("N");

        using var first = SingleInstance.TryAcquire(name);
        var second = SingleInstance.TryAcquire(name);

        Assert.NotNull(first);
        Assert.Null(second);
    }

    // Synchronous on purpose: the lock is a mutex, which only the thread that took it may release.
    [Fact]
    public void ASecondLaunchHandsItsSwitchesToTheRunningApp()
    {
        var name = "GoalMaker.Tests." + Guid.NewGuid().ToString("N");
        using var running = SingleInstance.TryAcquire(name);
        Assert.NotNull(running);
        using var received = new ManualResetEventSlim();
        IReadOnlyList<string> arguments = [];
        running.Listen(forwarded =>
        {
            arguments = forwarded;
            received.Set();
        });

        var reached = SingleInstance.Forward(name, ["--mini", "today", "goalmaker://open/plan"]);

        Assert.True(reached);
        Assert.True(received.Wait(TimeSpan.FromSeconds(10), TestContext.Current.CancellationToken));
        Assert.Equal(["--mini", "today", "goalmaker://open/plan"], arguments);
    }
}

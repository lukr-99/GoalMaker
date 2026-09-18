using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The quick-add box the global shortcut opens: a composer whose lines land in the Inbox.</summary>
public sealed class QuickAddTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ALineWithoutADayLandsInTheInboxAndTheBoxCanClose()
    {
        var composer = QuickAdd();
        var added = 0;
        composer.Added += (_, _) => added++;

        composer.NewTaskTitle = "Look up the train times";
        composer.AddTaskCommand.Execute(null);

        var task = planner.Task("Look up the train times");
        Assert.Null(task.PlannedDate);
        Assert.Equal(1, added);
        Assert.Equal(string.Empty, composer.NewTaskTitle);
    }

    [Fact]
    public void ALineThatNamesADayKeepsIt()
    {
        var composer = QuickAdd();
        composer.NewTaskTitle = "Call the bank tomorrow 17:00";
        composer.AddTaskCommand.Execute(null);

        var task = planner.Task("Call the bank");
        Assert.Equal(new DateOnly(2026, 9, 19), task.PlannedDate);
        Assert.Equal(new TimeOnly(17, 0), task.PlannedTime);
    }

    [Fact]
    public void AnEmptyLineSavesNothingAndKeepsTheBoxOpen()
    {
        var composer = QuickAdd();
        var added = 0;
        composer.Added += (_, _) => added++;

        composer.NewTaskTitle = "   ";

        Assert.False(composer.AddTaskCommand.CanExecute(null));
        Assert.Equal(0, added);
        Assert.Empty(planner.Tasks.All());
    }

    private ComposerViewModel QuickAdd() => new(
        planner.Tasks, planner.Areas, planner.Tags, planner.Settings, planner.Strings, planner.Time, _ => null, _ => null, action => action());
}

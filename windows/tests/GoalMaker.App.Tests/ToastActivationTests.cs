using GoalMaker.App.Shell;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>What a reminder toast's buttons carry, and what comes back when one is clicked.</summary>
public sealed class ToastActivationTests
{
    private const string Reminder = "11111111-2222-4333-8444-555555555555";

    [Theory]
    [InlineData(ToastAction.Open, null)]
    [InlineData(ToastAction.Done, null)]
    [InlineData(ToastAction.Dismiss, null)]
    [InlineData(ToastAction.Snooze, Snooze.TenMinutes)]
    [InlineData(ToastAction.Snooze, Snooze.OneHour)]
    [InlineData(ToastAction.Snooze, Snooze.TomorrowMorning)]
    public void EveryButtonRoundTrips(ToastAction action, Snooze? snooze)
    {
        var activation = new ToastActivation(action, Reminder, snooze);

        Assert.Equal(activation, ToastActivation.Parse(activation.Arguments));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("action=done")]
    [InlineData("reminder=abc")]
    [InlineData("action=explode;reminder=abc")]
    [InlineData("action=Snooze;reminder=abc")]
    [InlineData("action=Snooze;reminder=abc;snooze=Forever")]
    [InlineData("action=Done;reminder=")]
    public void ArgumentsThatArentAReminderAreIgnored(string? arguments)
    {
        Assert.Null(ToastActivation.Parse(arguments));
    }
}

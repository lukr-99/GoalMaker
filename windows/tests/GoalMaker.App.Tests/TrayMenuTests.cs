using System.Windows;
using System.Windows.Controls;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;

namespace GoalMaker.App.Tests;

/// <summary>The tray's right-click menu, built with the tray kit's menu builder.</summary>
public sealed class TrayMenuTests
{
    [Fact]
    public void TheItemsComeInOrderWithOpenAsTheDefault() => StaThread.Run(() =>
    {
        var menu = TrayMenu.Build(new TestPlanner.FormatStrings(), () => { }, () => { }, _ => { }, () => { });

        var lines = menu.Items.Cast<Control>()
            .Select(item => item is MenuItem menuItem ? (string)menuItem.Header : "-")
            .ToList();
        Assert.Equal(["Tray.Open", "Tray.QuickAdd", "-", "Tray.MiniToday", "Tray.MiniHabits", "-", "Tray.Quit"], lines);

        var items = menu.Items.OfType<MenuItem>().ToList();
        Assert.Equal(FontWeights.SemiBold, items[0].FontWeight);
        Assert.All(items.Skip(1), item => Assert.Equal(FontWeights.Normal, item.FontWeight));
        Assert.All(items, item => Assert.False(item.IsCheckable));
    });

    [Fact]
    public void EachItemRunsItsAction() => StaThread.Run(() =>
    {
        var done = new List<string>();
        var menu = TrayMenu.Build(
            new TestPlanner.FormatStrings(),
            () => done.Add("open"),
            () => done.Add("quick add"),
            page => done.Add(page.ToString()),
            () => done.Add("quit"));

        foreach (var item in menu.Items.OfType<MenuItem>())
        {
            item.RaiseEvent(new RoutedEventArgs(MenuItem.ClickEvent));
        }

        Assert.Equal(["open", "quick add", nameof(MiniPage.Today), nameof(MiniPage.Habits), "quit"], done);
    });

    [Fact]
    public void AnUnderscoreInALabelIsShownAsWritten() => StaThread.Run(() =>
    {
        var menu = TrayMenu.Build(new UnderscoreStrings(), () => { }, () => { }, _ => { }, () => { });

        // A plain string header reads "_" as an access key; the kit doubles it so it shows.
        Assert.Equal("Open__GoalMaker", (string)menu.Items.OfType<MenuItem>().First().Header);
    });

    private sealed class UnderscoreStrings : Localization.IStrings
    {
        public string Get(string key, params object[] arguments) => key == "Tray.Open" ? "Open_GoalMaker" : key;
    }
}

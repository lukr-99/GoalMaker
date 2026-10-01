using System.Windows;
using GoalMaker.App.Theming;
using Wpf.Ui.Markup;

namespace GoalMaker.App.Tests;

/// <summary>
/// The app's resources: the tray kit's dictionaries first, then GoalMaker's own. This builds them on a
/// plain resource dictionary, never on the App class, which would start the app. PageSnapshots renders
/// every page over the same merge on a plain Application.
/// </summary>
public sealed class AppResourcesTests
{
    [Fact]
    public void TheKitsDictionariesComeBeforeGoalMakersOwn() => StaThread.Run(() =>
    {
        var resources = Merged();

        var merged = resources.MergedDictionaries;
        Assert.Equal(3 + AppResources.OwnDictionaries.Count, merged.Count);
        Assert.IsType<ThemesDictionary>(merged[0]);
        Assert.IsType<ControlsDictionary>(merged[1]);
        Assert.EndsWith("Themes/Tray.xaml", merged[2].Source.OriginalString, StringComparison.Ordinal);
        for (var index = 0; index < AppResources.OwnDictionaries.Count; index++)
        {
            Assert.EndsWith($"Resources/{AppResources.OwnDictionaries[index]}.xaml", merged[3 + index].Source.OriginalString, StringComparison.Ordinal);
        }
    });

    [Fact]
    public void APlainWindowGetsTheKitsWindowStyle() => StaThread.Run(() =>
    {
        var resources = Merged();

        // The kit replaces WPF UI's implicit Window style, which breaks a window made in code.
        var style = Assert.IsType<Style>(resources[typeof(Window)]);
        Assert.Contains(style.Setters.OfType<Setter>(), setter => setter.Property == Window.BackgroundProperty);
    });

    [Fact]
    public void GoalMakersStringsAreThere() => StaThread.Run(() =>
    {
        var resources = Merged();

        Assert.Equal("Open GoalMaker", resources["Tray.Open"]);
    });

    private static ResourceDictionary Merged()
    {
        // WPF UI and the kit find their dictionaries by pack URIs, which Application's static constructor registers.
        _ = Application.Current;
        var resources = new ResourceDictionary();
        AppResources.Merge(resources);
        return resources;
    }
}

using System.Windows;
using System.Windows.Controls;
using GoalMaker.App.Shell;

namespace GoalMaker.App.Tests;

/// <summary>
/// The pages NavigationView gets scroll themselves; WPF UI would otherwise wrap them in its own
/// scroll viewer, and the mouse wheel would only work over its scroll bar.
/// </summary>
public sealed class PageProviderTests
{
    [Fact]
    public void EveryPageIsMarkedAsScrollingItselfAndMadeOnce()
    {
        var made = 0;
        var provider = new PageProvider(new Dictionary<Type, Func<object>>
        {
            [typeof(FakePage)] = () =>
            {
                made++;
                var page = new FakePage();
                ScrollViewer.SetCanContentScroll(page, true);
                return page;
            },
        });

        var page = (DependencyObject)provider.GetPage(typeof(FakePage))!;

        Assert.False(ScrollViewer.GetCanContentScroll(page));
        Assert.Same(page, provider.GetPage(typeof(FakePage)));
        Assert.Equal(1, made);
        Assert.Null(provider.GetPage(typeof(string)));
    }

    // A DependencyObject needs no STA thread, unlike a real Page.
    private sealed class FakePage : DependencyObject;
}

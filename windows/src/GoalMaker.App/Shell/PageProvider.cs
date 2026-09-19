using System.Windows;
using System.Windows.Controls;
using Wpf.Ui.Abstractions;

namespace GoalMaker.App.Shell;

/// <summary>
/// Hands NavigationView the pages built by the composition root instead of letting it new them up.
/// Every page scrolls itself (its own ScrollViewer, a composer pinned under a list), so each is marked
/// as not content-scrolling: otherwise WPF UI wraps it in a scroll viewer of its own, the page gets
/// endless height, and the page's ScrollViewer takes the mouse wheel without being able to scroll.
/// </summary>
public sealed class PageProvider(IReadOnlyDictionary<Type, Func<object>> factories) : INavigationViewPageProvider
{
    private readonly Dictionary<Type, object> created = [];

    public object? GetPage(Type pageType)
    {
        if (created.TryGetValue(pageType, out var page))
        {
            return page;
        }

        if (!factories.TryGetValue(pageType, out var factory))
        {
            return null;
        }

        page = factory();
        if (page is DependencyObject element)
        {
            ScrollViewer.SetCanContentScroll(element, false);
        }

        created[pageType] = page;
        return page;
    }
}

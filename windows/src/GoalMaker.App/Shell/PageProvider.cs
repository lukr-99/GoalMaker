using Wpf.Ui.Abstractions;

namespace GoalMaker.App.Shell;

/// <summary>Hands NavigationView the pages built by the composition root instead of letting it new them up.</summary>
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
        created[pageType] = page;
        return page;
    }
}

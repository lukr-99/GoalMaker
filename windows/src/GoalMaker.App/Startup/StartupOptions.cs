namespace GoalMaker.App.Startup;

/// <summary>
/// Launch switches (spec: Windows specifics). <c>--tray</c> starts hidden in the tray; <c>--open
/// today|settings</c> shows that page. A <c>goalmaker://open/today</c> link means the same. Unknown
/// values are ignored so older or newer shortcuts never stop the app from starting.
/// </summary>
public sealed record StartupOptions(bool StartInTray, AppPage? OpenPage)
{
    public static StartupOptions Parse(IReadOnlyList<string> arguments)
    {
        var tray = false;
        AppPage? page = null;
        for (var index = 0; index < arguments.Count; index++)
        {
            var argument = arguments[index].Trim();
            if (argument.Equals("--tray", StringComparison.OrdinalIgnoreCase))
            {
                tray = true;
            }
            else if (argument.Equals("--open", StringComparison.OrdinalIgnoreCase) && index + 1 < arguments.Count)
            {
                page = PageFrom(arguments[++index]) ?? page;
            }
            else if (argument.StartsWith("goalmaker://open/", StringComparison.OrdinalIgnoreCase))
            {
                page = PageFrom(argument["goalmaker://open/".Length..].TrimEnd('/')) ?? page;
            }
        }

        return new StartupOptions(tray && page is null, page);
    }

    private static AppPage? PageFrom(string name) => name.Trim().ToLowerInvariant() switch
    {
        "today" => AppPage.Today,
        "settings" => AppPage.Settings,
        _ => null,
    };
}

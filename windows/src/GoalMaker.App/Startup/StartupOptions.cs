namespace GoalMaker.App.Startup;

/// <summary>
/// Launch switches (spec: Windows specifics). <c>--tray</c> starts hidden in the tray; <c>--open
/// today|tomorrow|inbox|plan|settings|areas|archive|activity</c> shows that page; <c>--mini
/// today|habits</c> opens a mini window (docs/mini-windows.md); <c>--no-activate</c> shows the window
/// without taking the keyboard focus (start-up scripts, dev tooling). A <c>goalmaker://open/today</c>
/// or <c>goalmaker://mini/habits</c> link does the same. Unknown values are ignored so older or newer
/// shortcuts never stop the app from starting.
/// </summary>
public sealed record StartupOptions(bool StartInTray, AppPage? OpenPage, bool NoActivate = false, MiniPage? Mini = null)
{
    public static StartupOptions Parse(IReadOnlyList<string> arguments)
    {
        var tray = false;
        var noActivate = false;
        AppPage? page = null;
        MiniPage? mini = null;
        for (var index = 0; index < arguments.Count; index++)
        {
            var argument = arguments[index].Trim();
            if (argument.Equals("--tray", StringComparison.OrdinalIgnoreCase))
            {
                tray = true;
            }
            else if (argument.Equals("--no-activate", StringComparison.OrdinalIgnoreCase))
            {
                noActivate = true;
            }
            else if (argument.Equals("--open", StringComparison.OrdinalIgnoreCase) && index + 1 < arguments.Count)
            {
                page = PageFrom(arguments[++index]) ?? page;
            }
            else if (argument.Equals("--mini", StringComparison.OrdinalIgnoreCase) && index + 1 < arguments.Count)
            {
                mini = MiniFrom(arguments[++index]) ?? mini;
            }
            else if (argument.StartsWith("goalmaker://open/", StringComparison.OrdinalIgnoreCase))
            {
                page = PageFrom(argument["goalmaker://open/".Length..].TrimEnd('/')) ?? page;
            }
            else if (argument.StartsWith("goalmaker://mini/", StringComparison.OrdinalIgnoreCase))
            {
                mini = MiniFrom(argument["goalmaker://mini/".Length..].TrimEnd('/')) ?? mini;
            }
        }

        // A launch that only asks for a mini window leaves the main window where it was, so a shortcut
        // can put Today on the desktop without the whole app coming up.
        return new StartupOptions((tray || mini is not null) && page is null, page, noActivate, mini);
    }

    private static MiniPage? MiniFrom(string name) => name.Trim().ToLowerInvariant() switch
    {
        "today" => MiniPage.Today,
        "habits" => MiniPage.Habits,
        _ => null,
    };

    private static AppPage? PageFrom(string name) => name.Trim().ToLowerInvariant() switch
    {
        "today" => AppPage.Today,
        "tomorrow" => AppPage.Tomorrow,
        "inbox" => AppPage.Inbox,
        "plan" => AppPage.Plan,
        "settings" => AppPage.Settings,
        "areas" => AppPage.Areas,
        "archive" => AppPage.Archive,
        "activity" => AppPage.Activity,
        "goals" => AppPage.Goals,
        "habits" => AppPage.Habits,
        "reviews" => AppPage.Reviews,
        "stats" => AppPage.Stats,
        "projects" => AppPage.Projects,
        "calendar" => AppPage.Calendar,
        _ => null,
    };
}

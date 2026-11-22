using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using DotNetLib.Tray;
using GoalMaker.App.Theming;

namespace GoalMaker.App.Shell;

/// <summary>
/// Opens a web page in the owner's default browser. Windows can refuse (no default browser, a
/// blocked or odd link), and that must never crash the app: the owner gets a short message that
/// names the link instead (GM-103).
/// </summary>
public static class LinkOpener
{
    /// <summary>Opens <paramref name="url"/>; false when it could not be opened.</summary>
    public static bool Open(string url) => Open(url, Launch, Inform);

    /// <summary>The testable core: <paramref name="launch"/> starts the browser, <paramref name="inform"/> tells the owner.</summary>
    public static bool Open(string url, Action<string> launch, Action<string> inform)
    {
        try
        {
            launch(url);
            return true;
        }
        catch (Exception error) when (error is Win32Exception or InvalidOperationException or FileNotFoundException or UriFormatException or ArgumentException or PlatformNotSupportedException)
        {
            Trace.WriteLine("GoalMaker: a link could not be opened: " + error.Message);
            inform(url);
            return false;
        }
    }

    private static void Launch(string url) =>
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true })?.Dispose();

    private static void Inform(string url)
    {
        var app = Application.Current;
        var title = app?.TryFindResource("App.Name") as string ?? "GoalMaker";
        var template = app?.TryFindResource("Link.OpenFailed") as string ?? "This link could not be opened: {0}";
        TrayMessageWindow.Inform(title, string.Format(System.Globalization.CultureInfo.CurrentCulture, template, url), Theme.Attach);
    }
}

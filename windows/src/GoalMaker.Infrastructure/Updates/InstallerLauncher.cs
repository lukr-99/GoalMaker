using System.Diagnostics;
using GoalMaker.Core.Updates;

namespace GoalMaker.Infrastructure.Updates;

/// <summary>
/// Starts the verified Inno Setup installer. It closes the running app through the Restart Manager,
/// installs over it and starts it again; the app shuts itself down right after launching it.
/// </summary>
public sealed class InstallerLauncher(Action shutdownApp) : IUpdateInstaller
{
    public void Launch(string localPath)
    {
        using var process = Process.Start(new ProcessStartInfo(localPath)
        {
            Arguments = "/SILENT /SUPPRESSMSGBOXES /NORESTART",
            UseShellExecute = true,
        });
        shutdownApp();
    }
}

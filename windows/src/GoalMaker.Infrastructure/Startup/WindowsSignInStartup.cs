using GoalMaker.Core.Startup;
using Microsoft.Win32;

namespace GoalMaker.Infrastructure.Startup;

/// <summary>
/// GoalMaker's own "start in the tray when I sign in" (spec, story 81), kept where Windows looks for
/// per-user startup: one value named after this build under
/// <c>HKCU\Software\Microsoft\Windows\CurrentVersion\Run</c>, the same value the installer writes, so
/// the box in Settings and the box in the installer are one thing. Nothing else in that key is read
/// or touched.
/// </summary>
public sealed class WindowsSignInStartup : ISignInStartup
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    private readonly string valueName;
    private readonly string command;

    /// <param name="valueName">GoalMaker's own value name; dev builds use their own.</param>
    /// <param name="executable">The GoalMaker to start; it starts in the tray.</param>
    public WindowsSignInStartup(string valueName, string executable)
    {
        this.valueName = valueName;
        command = $"\"{executable}\" --tray";
    }

    public bool IsOn
    {
        get
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKey);
            return key?.GetValue(valueName) is string;
        }
    }

    public bool Set(bool on)
    {
        try
        {
            using var key = Registry.CurrentUser.CreateSubKey(RunKey);
            if (on)
            {
                key.SetValue(valueName, command, RegistryValueKind.String);
            }
            else
            {
                key.DeleteValue(valueName, throwOnMissingValue: false);
            }

            return true;
        }
        catch (Exception error) when (error is UnauthorizedAccessException or System.Security.SecurityException)
        {
            return false;
        }
    }
}

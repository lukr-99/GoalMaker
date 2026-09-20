using System.Diagnostics;
using System.IO;
using GoalMaker.Core.Startup;
using Microsoft.Win32;

namespace GoalMaker.Infrastructure.Startup;

/// <summary>
/// Finds Startup Profiles the way that app publishes itself and asks it to add GoalMaker (its
/// integration contract; spec, story 84). It is found by the <c>startupprofiles://</c> handler it
/// registers under the current user's classes, and failing that at the folder its installer uses.
/// GoalMaker only starts that app with a registration link: the window that follows is theirs, and
/// nothing here writes to their files or keys.
/// </summary>
public sealed class WindowsStartupProfiles : IStartupProfiles
{
    private const string CommandKey = $@"Software\Classes\{StartupProfilesLink.Scheme}\shell\open\command";
    private const string InstalledFolder = @"Programs\StartupProfiles";
    private const string Executable = "StartupProfiles.exe";

    private readonly Func<string?> registeredCommand;
    private readonly Func<string, bool> exists;
    private readonly Func<string, string, bool> start;

    public WindowsStartupProfiles()
        : this(ReadRegisteredCommand, File.Exists, Start)
    {
    }

    /// <param name="registeredCommand">The command line registered for the scheme, if any.</param>
    /// <param name="exists">Whether a file is there.</param>
    /// <param name="start">Runs the app with one argument; false when it could not be started.</param>
    public WindowsStartupProfiles(Func<string?> registeredCommand, Func<string, bool> exists, Func<string, string, bool> start)
    {
        this.registeredCommand = registeredCommand;
        this.exists = exists;
        this.start = start;
    }

    public string? Find()
    {
        if (ExecutableIn(registeredCommand()) is { } registered && exists(registered))
        {
            return registered;
        }

        var installed = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), InstalledFolder, Executable);
        return exists(installed) ? installed : null;
    }

    public bool Ask(StartupProfilesRequest request) =>
        Find() is { } app && start(app, StartupProfilesLink.Register(request));

    /// <summary>
    /// The executable a registered command line runs: the first quoted part, or the text up to the
    /// first space when it is not quoted. Null when there is no command.
    /// </summary>
    public static string? ExecutableIn(string? command)
    {
        var text = command?.Trim();
        if (string.IsNullOrEmpty(text))
        {
            return null;
        }

        if (text[0] == '"')
        {
            var end = text.IndexOf('"', 1);
            return end > 1 ? text[1..end] : null;
        }

        var space = text.IndexOf(' ', StringComparison.Ordinal);
        var path = space < 0 ? text : text[..space];
        return path.Length == 0 ? null : path;
    }

    private static string? ReadRegisteredCommand()
    {
        using var key = Registry.CurrentUser.OpenSubKey(CommandKey);
        return key?.GetValue(null) as string;
    }

    private static bool Start(string app, string link)
    {
        try
        {
            // The link is passed to the app itself, so it works whether or not the scheme is
            // registered with Windows yet; Startup Profiles then opens its own confirmation window.
            using var process = Process.Start(new ProcessStartInfo(app, link) { UseShellExecute = false });
            return process is not null;
        }
        catch (Exception error) when (error is System.ComponentModel.Win32Exception or InvalidOperationException or IOException)
        {
            return false;
        }
    }
}

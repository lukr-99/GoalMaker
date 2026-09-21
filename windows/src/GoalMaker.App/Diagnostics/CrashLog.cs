using System.Globalization;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace GoalMaker.App.Diagnostics;

/// <summary>
/// Writes unhandled exceptions to logs/crash.log in the app's data folder, so a crash on start-up
/// leaves something to read. Holds no personal data beyond what an exception message contains.
/// </summary>
public static class CrashLog
{
    public static void Install(Application application, string dataFolder)
    {
        var path = Path.Combine(dataFolder, "logs", "crash.log");
        application.DispatcherUnhandledException += (_, e) => Write(path, e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Write(path, e.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (_, e) => Write(path, e.Exception);
    }

    /// <summary>Writes one exception, for a failure that happens before anything is installed.</summary>
    public static void Write(string path, Exception? error)
    {
        if (error is null)
        {
            return;
        }

        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            File.AppendAllText(
                path,
                $"{DateTimeOffset.Now.ToString("O", CultureInfo.InvariantCulture)} {error}{Environment.NewLine}{Environment.NewLine}");
        }
        catch (IOException)
        {
            // Logging must never crash the app a second time.
        }
    }
}

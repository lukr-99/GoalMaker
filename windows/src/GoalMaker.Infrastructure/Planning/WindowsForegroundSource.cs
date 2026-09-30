using System.Runtime.InteropServices;
using GoalMaker.Core.Planning;
using Microsoft.Win32;

namespace GoalMaker.Infrastructure.Planning;

/// <summary>
/// Tally's view of the PC through Win32 (docs/tally.md): a WinEvent hook says when another window comes
/// to the front, the window's process names the app, <c>GetLastInputInfo</c> gives the idle time, and
/// the session and power events say when the PC locks or sleeps. It reads only when asked; the tracker
/// asks every 15 seconds. Start and Stop run on the UI thread, whose message loop delivers the hook.
/// </summary>
public sealed class WindowsForegroundSource : IForegroundSource
{
    private const uint ForegroundEvent = 0x0003;
    private const uint OutOfContext = 0x0000;
    private const uint QueryLimitedInformation = 0x1000;
    private const int LongestPath = 1024;

    // The hook holds on to the delegate only as a pointer, so this field keeps it alive.
    private readonly WinEventProc callback;
    private IntPtr hook;
    private bool started;

    public WindowsForegroundSource() => callback = OnWinEvent;

    private delegate void WinEventProc(IntPtr hook, uint type, IntPtr window, int objectId, int childId, uint thread, uint time);

    public event EventHandler? Switched;

    public event EventHandler<bool>? LockChanged;

    public event EventHandler<bool>? SleepChanged;

    public ForegroundApp? Current()
    {
        var window = GetForegroundWindow();
        if (window == IntPtr.Zero)
        {
            return null;
        }

        _ = GetWindowThreadProcessId(window, out var processId);
        return new ForegroundApp(ExecutableOf(processId), TitleOf(window));
    }

    public TimeSpan SinceInput()
    {
        var info = new LastInputInfo { Size = (uint)Marshal.SizeOf<LastInputInfo>() };

        // Both are milliseconds since the PC started, and wrap together after 49 days.
        return GetLastInputInfo(ref info)
            ? TimeSpan.FromMilliseconds(unchecked((uint)Environment.TickCount - info.Time))
            : TimeSpan.Zero;
    }

    public void Start()
    {
        if (started)
        {
            return;
        }

        started = true;
        hook = SetWinEventHook(ForegroundEvent, ForegroundEvent, IntPtr.Zero, callback, 0, 0, OutOfContext);
        SystemEvents.SessionSwitch += OnSessionSwitch;
        SystemEvents.PowerModeChanged += OnPowerModeChanged;
    }

    public void Stop()
    {
        if (!started)
        {
            return;
        }

        started = false;
        SystemEvents.SessionSwitch -= OnSessionSwitch;
        SystemEvents.PowerModeChanged -= OnPowerModeChanged;
        if (hook != IntPtr.Zero)
        {
            _ = UnhookWinEvent(hook);
            hook = IntPtr.Zero;
        }
    }

    // The executable's file name (code.exe); empty when the process won't say, as an elevated one may not.
    private static string ExecutableOf(uint processId)
    {
        var process = OpenProcess(QueryLimitedInformation, false, processId);
        if (process == IntPtr.Zero)
        {
            return string.Empty;
        }

        try
        {
            var buffer = new char[LongestPath];
            var size = (uint)buffer.Length;
            return QueryFullProcessImageName(process, 0, buffer, ref size) ? Path.GetFileName(new string(buffer, 0, (int)size)) : string.Empty;
        }
        finally
        {
            _ = CloseHandle(process);
        }
    }

    private static string? TitleOf(IntPtr window)
    {
        var length = GetWindowTextLength(window);
        if (length <= 0)
        {
            return null;
        }

        var buffer = new char[length + 1];
        var read = GetWindowText(window, buffer, buffer.Length);
        return read > 0 ? new string(buffer, 0, read) : null;
    }

    private void OnWinEvent(IntPtr hook, uint type, IntPtr window, int objectId, int childId, uint thread, uint time) =>
        Switched?.Invoke(this, EventArgs.Empty);

    private void OnSessionSwitch(object? sender, SessionSwitchEventArgs e)
    {
        if (e.Reason == SessionSwitchReason.SessionLock)
        {
            LockChanged?.Invoke(this, true);
        }
        else if (e.Reason == SessionSwitchReason.SessionUnlock)
        {
            LockChanged?.Invoke(this, false);
        }
    }

    private void OnPowerModeChanged(object? sender, PowerModeChangedEventArgs e)
    {
        if (e.Mode == PowerModes.Suspend)
        {
            SleepChanged?.Invoke(this, true);
        }
        else if (e.Mode == PowerModes.Resume)
        {
            SleepChanged?.Invoke(this, false);
        }
    }

    [DllImport("user32.dll")]
    private static extern IntPtr SetWinEventHook(uint eventMin, uint eventMax, IntPtr module, WinEventProc callback, uint processId, uint threadId, uint flags);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool UnhookWinEvent(IntPtr hook);

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowTextLength(IntPtr window);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, [Out] char[] text, int length);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetLastInputInfo(ref LastInputInfo info);

    [DllImport("kernel32.dll")]
    private static extern IntPtr OpenProcess(uint access, [MarshalAs(UnmanagedType.Bool)] bool inherit, uint processId);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, EntryPoint = "QueryFullProcessImageNameW")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryFullProcessImageName(IntPtr process, uint flags, [Out] char[] name, ref uint size);

    [DllImport("kernel32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);

    [StructLayout(LayoutKind.Sequential)]
    private struct LastInputInfo
    {
        public uint Size;
        public uint Time;
    }
}

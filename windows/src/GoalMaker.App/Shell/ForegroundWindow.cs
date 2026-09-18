using System.Runtime.InteropServices;

namespace GoalMaker.App.Shell;

/// <summary>The window that has the keyboard, so the quick-add box can hand it back when it closes.</summary>
internal static class ForegroundWindow
{
    public static IntPtr Current() => GetForegroundWindow();

    public static void Restore(IntPtr window)
    {
        if (window != IntPtr.Zero)
        {
            _ = SetForegroundWindow(window);
        }
    }

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetForegroundWindow(IntPtr window);
}

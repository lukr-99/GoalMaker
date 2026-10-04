using System.Runtime.InteropServices;

namespace GoalMaker.App.Shell;

/// <summary>
/// The window that has the keyboard, so the quick-add box and the mini windows can hand it back when
/// they close.
/// </summary>
internal static class ForegroundWindow
{
    public static IntPtr Current() => GetForegroundWindow();

    /// <summary>Gives the keyboard back to <paramref name="window"/>, if it is still there to take it.</summary>
    public static void Restore(IntPtr window)
    {
        if (window != IntPtr.Zero && IsWindowVisible(window))
        {
            _ = SetForegroundWindow(window);
        }
    }

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetForegroundWindow(IntPtr window);

    // False for a window that has closed or hidden since (a menu that is gone, a closed dialog).
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool IsWindowVisible(IntPtr window);
}

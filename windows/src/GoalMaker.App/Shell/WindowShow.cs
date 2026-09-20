using System.Windows;

namespace GoalMaker.App.Shell;

/// <summary>
/// How to put the main window on screen. WPF refuses to show a window that is maximized without
/// activating it, so a window left maximized and asked for with <c>--no-activate</c> goes up normal
/// and is maximized once it is on screen. A window left minimized comes back normal.
/// </summary>
public static class WindowShow
{
    /// <summary>The state to show in, and the state to set once it is on screen.</summary>
    public static (WindowState Show, WindowState After) Plan(bool activate, WindowState state) => state switch
    {
        WindowState.Maximized when !activate => (WindowState.Normal, WindowState.Maximized),
        WindowState.Minimized => (WindowState.Normal, WindowState.Normal),
        _ => (state, state),
    };
}

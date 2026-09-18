using NHotkey;
using NHotkey.Wpf;

namespace GoalMaker.App.Shell;

/// <summary>
/// The global quick-add shortcut (spec, story 11), registered with Windows through NHotkey. Another app
/// can already own a shortcut; <see cref="Apply"/> then reports it instead of failing the app.
/// </summary>
public sealed class QuickAddHotkey(Action pressed) : IDisposable
{
    private const string Name = "GoalMaker.QuickAdd";
    private bool registered;

    /// <summary>The shortcut in use, or null when none is.</summary>
    public HotkeyGesture? Current { get; private set; }

    /// <summary>Registers <paramref name="gesture"/> in place of the last one; null turns the shortcut off. False when Windows refused it.</summary>
    public bool Apply(HotkeyGesture? gesture)
    {
        Remove();
        if (gesture is null)
        {
            return true;
        }

        try
        {
            HotkeyManager.Current.AddOrReplace(Name, gesture.Key, gesture.Modifiers, (_, e) =>
            {
                e.Handled = true;
                pressed();
            });
            registered = true;
            Current = gesture;
            return true;
        }
        catch (HotkeyAlreadyRegisteredException)
        {
            return false;
        }
    }

    public void Dispose() => Remove();

    private void Remove()
    {
        if (registered)
        {
            HotkeyManager.Current.Remove(Name);
            registered = false;
        }

        Current = null;
    }
}

using System.Windows.Input;

namespace GoalMaker.App.Shell;

/// <summary>
/// A global shortcut such as <c>Win+Alt+Space</c>: at least one modifier and one other key. Stored in
/// the settings as that text, so the Core layer never sees WPF's key types.
/// </summary>
public sealed record HotkeyGesture(ModifierKeys Modifiers, Key Key)
{
    /// <summary>The quick-add shortcut GoalMaker starts with (spec: Windows specifics).</summary>
    public static HotkeyGesture Default { get; } = new(ModifierKeys.Windows | ModifierKeys.Alt, Key.Space);

    /// <summary>A gesture from keys pressed together, or null when they can't make a global shortcut.</summary>
    public static HotkeyGesture? FromKeys(ModifierKeys modifiers, Key key) =>
        modifiers == ModifierKeys.None || IsModifier(key) || key is Key.None or Key.System or Key.ImeProcessed or Key.DeadCharProcessed
            ? null
            : new HotkeyGesture(modifiers, key);

    /// <summary>Reads text like <c>Win+Alt+Space</c> or <c>ctrl+shift+k</c> back; null when it isn't a shortcut.</summary>
    public static HotkeyGesture? Parse(string? text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return null;
        }

        var modifiers = ModifierKeys.None;
        Key? key = null;
        foreach (var part in text.Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            switch (part.ToLowerInvariant())
            {
                case "win" or "windows":
                    modifiers |= ModifierKeys.Windows;
                    break;
                case "ctrl" or "control":
                    modifiers |= ModifierKeys.Control;
                    break;
                case "alt":
                    modifiers |= ModifierKeys.Alt;
                    break;
                case "shift":
                    modifiers |= ModifierKeys.Shift;
                    break;
                default:
                    if (key is not null || KeyFrom(part) is not { } parsed)
                    {
                        return null;
                    }

                    key = parsed;
                    break;
            }
        }

        return key is { } found ? FromKeys(modifiers, found) : null;
    }

    /// <summary>The gesture as people write it, modifiers first: <c>Win+Ctrl+Alt+Shift+K</c>.</summary>
    public override string ToString()
    {
        var parts = new List<string>();
        if (Modifiers.HasFlag(ModifierKeys.Windows))
        {
            parts.Add("Win");
        }

        if (Modifiers.HasFlag(ModifierKeys.Control))
        {
            parts.Add("Ctrl");
        }

        if (Modifiers.HasFlag(ModifierKeys.Alt))
        {
            parts.Add("Alt");
        }

        if (Modifiers.HasFlag(ModifierKeys.Shift))
        {
            parts.Add("Shift");
        }

        parts.Add(Key is >= Key.D0 and <= Key.D9 ? ((int)(Key - Key.D0)).ToString(System.Globalization.CultureInfo.InvariantCulture) : Key.ToString());
        return string.Join('+', parts);
    }

    private static Key? KeyFrom(string text) =>
        text.Length == 1 && char.IsAsciiDigit(text[0]) ? Key.D0 + (text[0] - '0')
        : Enum.TryParse<Key>(text, ignoreCase: true, out var key) && !int.TryParse(text, out _) ? key
        : null;

    private static bool IsModifier(Key key) => key is Key.LeftCtrl or Key.RightCtrl or Key.LeftAlt or Key.RightAlt
        or Key.LeftShift or Key.RightShift or Key.LWin or Key.RWin;
}

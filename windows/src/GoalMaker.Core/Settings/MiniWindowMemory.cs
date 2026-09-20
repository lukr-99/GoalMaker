namespace GoalMaker.Core.Settings;

/// <summary>
/// What each mini window remembers between launches (spec, story 80): where it was and whether it
/// was pinned. One window's memory never disturbs another's.
/// </summary>
public static class MiniWindowMemory
{
    /// <summary>What this window remembered, or null the first time it opens.</summary>
    public static MiniWindowState? Of(ISettingsStore settings, string name) =>
        settings.MiniWindows.TryGetValue(name, out var state) ? state : null;

    /// <summary>Keeps this window's place and pin, leaving every other window's alone.</summary>
    public static void Remember(ISettingsStore settings, string name, MiniWindowState state) =>
        settings.MiniWindows = new Dictionary<string, MiniWindowState>(settings.MiniWindows, StringComparer.Ordinal)
        {
            [name] = state,
        };
}

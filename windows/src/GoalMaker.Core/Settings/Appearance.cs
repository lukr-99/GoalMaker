namespace GoalMaker.Core.Settings;

/// <summary>
/// How the app looks on this PC (docs/design/spec.md, "Settings"). Not synced. <see cref="ThemeId"/>
/// names a theme in contracts/design/themes.json; unknown ids fall back to the default theme.
/// </summary>
public sealed record Appearance(string? ThemeId, ThemeMode Mode, bool PureBlack, ReduceMotion ReduceMotion, bool CompletionSound)
{
    /// <summary>A fresh install: the default theme, following Windows' light or dark.</summary>
    public static Appearance Default { get; } = new(null, ThemeMode.System, PureBlack: false, ReduceMotion.System, CompletionSound: false);
}

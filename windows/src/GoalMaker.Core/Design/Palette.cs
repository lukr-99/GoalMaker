namespace GoalMaker.Core.Design;

/// <summary>
/// One theme's colors for one mode, by role (contracts/design/themes.json, "roles"), as opaque ARGB
/// values so Core stays free of UI types.
/// </summary>
public sealed record Palette(
    uint Background,
    uint Surface,
    uint SurfaceVariant,
    uint Text,
    uint TextMuted,
    uint Outline,
    uint Primary,
    uint OnPrimary,
    uint Accent,
    uint OnAccent,
    uint Hero,
    uint OnHero,
    uint HeroAccent,
    uint Danger);

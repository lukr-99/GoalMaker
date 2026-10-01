using DotNetLib.Tray;
using GoalMaker.Core.Design;

namespace GoalMaker.App.Theming;

/// <summary>
/// A GoalMaker palette (contracts/design/themes.json) as the tray kit's <see cref="TrayPalette"/>, so
/// the kit's Tray.* brushes, its dialogs and its Window style wear the theme's colors instead of the
/// kit's neutral grey and blue. GoalMaker has no success or warning color: success is the accent (the
/// checks and progress), warning is danger, focus is the primary. themes.json holds every pair used
/// here to WCAG AA (tools/check_design_tokens.py).
/// </summary>
public static class TrayPalettes
{
    public static TrayPalette From(Palette palette)
    {
        ArgumentNullException.ThrowIfNull(palette);

        return new TrayPalette(
            Background: ThemeApplier.ToColor(palette.Background),
            Surface: ThemeApplier.ToColor(palette.Surface),
            SurfaceRaised: ThemeApplier.ToColor(palette.SurfaceVariant),
            TextPrimary: ThemeApplier.ToColor(palette.Text),
            TextSecondary: ThemeApplier.ToColor(palette.TextMuted),
            Border: ThemeApplier.ToColor(palette.Outline),
            Primary: ThemeApplier.ToColor(palette.Primary),
            OnPrimary: ThemeApplier.ToColor(palette.OnPrimary),
            Danger: ThemeApplier.ToColor(palette.Danger),
            Success: ThemeApplier.ToColor(palette.Accent),
            Warning: ThemeApplier.ToColor(palette.Danger),
            Focus: ThemeApplier.ToColor(palette.Primary));
    }

    /// <summary>The light palette and the dark one (pure black when asked) of <paramref name="theme"/>.</summary>
    public static (TrayPalette Light, TrayPalette Dark) For(ThemeDefinition theme, bool pureBlack)
    {
        ArgumentNullException.ThrowIfNull(theme);

        return (From(theme.Light), From(pureBlack ? theme.Black : theme.Dark));
    }
}

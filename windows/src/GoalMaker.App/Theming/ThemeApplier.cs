using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using DotNetLib.Tray;
using GoalMaker.App.Controls;
using GoalMaker.Core.Design;
using GoalMaker.Core.Settings;
using ThemeMode = GoalMaker.Core.Settings.ThemeMode;

namespace GoalMaker.App.Theming;

/// <summary>
/// Applies the chosen theme from contracts/design/themes.json (ADR 0008) in light, dark or pure
/// black: GoalMaker's own resources (GM.* brushes, fonts, corners, spacing), and WPF UI's theme and
/// the resource keys its controls use, so built-in controls match. Views read GM.* keys through
/// DynamicResource, so switching applies at once. With the <paramref name="logo"/> mark it also sets
/// the theme's logo colors and renders the logo as the window and tray icon (<see cref="LogoIcon"/>).
/// <para>
/// The tray kit's <see cref="TrayThemeApplier"/> does the part every tray app shares: WPF UI's light,
/// dark or high-contrast theme, the kit's Tray.* brushes in the theme's colors
/// (<see cref="TrayPalettes"/>), and following Windows' light or dark while the mode is System. Its
/// palettes are fixed when it is made, so a switch to another theme or to pure black makes a new one.
/// Every apply of the kit's, including one Windows started, runs GoalMaker's own part after it.
/// </para>
/// </summary>
public sealed class ThemeApplier : IDisposable
{
    private readonly ResourceDictionary resources;
    private readonly LogoMark? logo;
    private readonly Func<bool> systemIsDark;
    private Appearance current = Appearance.Default;
    private TrayThemeApplier? kit;
    private (string ThemeId, bool PureBlack)? kitPalettes;

    /// <param name="tokens">The themes from contracts/design/themes.json.</param>
    /// <param name="resources">The application's resources, where every key is set.</param>
    /// <param name="logo">The logo mark, or null for no logo and no icons.</param>
    /// <param name="systemIsDark">Whether Windows is dark; <see cref="TrayThemeApplier.WindowsAppsUseDark"/> when null. A test passes its own.</param>
    public ThemeApplier(DesignTokens tokens, ResourceDictionary resources, LogoMark? logo = null, Func<bool>? systemIsDark = null)
    {
        Tokens = tokens;
        this.resources = resources;
        this.logo = logo;
        this.systemIsDark = systemIsDark ?? TrayThemeApplier.WindowsAppsUseDark;
    }

    public DesignTokens Tokens { get; }

    /// <summary>Whether the last applied appearance came out dark (for previews of the other themes).</summary>
    public bool IsDark { get; private set; }

    /// <summary>Whether animations should become short fades (the system setting or the in-app switch).</summary>
    public bool MotionReduced { get; private set; }

    /// <summary>The logo in the current theme's colors, for the window and the taskbar; null without a mark.</summary>
    public ImageSource? LogoIcon { get; private set; }

    /// <summary>The same logo as an .ico file's bytes, for the tray; null without a mark.</summary>
    public byte[]? LogoIconFile { get; private set; }

    /// <summary>After every Apply, so views can rebuild what they colored in code (area dots, the tray icon).</summary>
    public event EventHandler? Applied;

    /// <summary>
    /// The body font becomes the window's default. The window's background stays WPF UI's (it sets it
    /// in code); MainWindow's root grid paints GM.BackgroundBrush over it, title bar included.
    /// </summary>
    public void Attach(Window target) => target.SetResourceReference(Control.FontFamilyProperty, "GM.BodyFont");

    /// <summary>The saved mode as the tray kit's.</summary>
    public static TrayThemeMode ToTrayMode(ThemeMode mode) => mode switch
    {
        ThemeMode.Light => TrayThemeMode.Light,
        ThemeMode.Dark => TrayThemeMode.Dark,
        _ => TrayThemeMode.System,
    };

    public void Apply(Appearance appearance)
    {
        current = appearance;
        var theme = Tokens.Theme(appearance.ThemeId);
        if (kit is null || kitPalettes != (theme.Id, appearance.PureBlack))
        {
            if (kit is not null)
            {
                kit.Applied -= OnKitApplied;
                kit.Dispose();
            }

            var (light, dark) = TrayPalettes.For(theme, appearance.PureBlack);
            kit = new TrayThemeApplier(resources, systemIsDark, light, dark);
            kit.Applied += OnKitApplied;
            kitPalettes = (theme.Id, appearance.PureBlack);
        }

        // WPF UI's theme and the Tray.* brushes, then GoalMaker's part in OnKitApplied.
        kit.Apply(ToTrayMode(appearance.Mode));
    }

    public void Dispose()
    {
        if (kit is not null)
        {
            kit.Applied -= OnKitApplied;
            kit.Dispose();
        }
    }

    // Runs after every apply of the kit's: from Apply, and when Windows turns light or dark in System mode.
    private void OnKitApplied(object? sender, EventArgs e)
    {
        var appearance = current;
        IsDark = kit!.IsDark;
        var theme = Tokens.Theme(appearance.ThemeId);
        var palette = !IsDark ? theme.Light : appearance.PureBlack ? theme.Black : theme.Dark;

        SetAccent(palette);
        SetColors(palette);
        SetFonts(theme.Typography);
        SetShapes(theme.Shapes);
        SetDensity(Tokens.Density);
        resources["GM.HeadlineUppercase"] = theme.Typography.Heading.Uppercase;
        resources["GM.IsDark"] = IsDark;
        MotionReduced = appearance.ReduceMotion switch
        {
            ReduceMotion.On => true,
            ReduceMotion.Off => false,
            _ => !SystemParameters.ClientAreaAnimation,
        };
        resources["GM.ReduceMotion"] = MotionReduced;
        SetMotion(Tokens.Motion);
        SetHighlight(IsDark ? theme.Highlight.Dark : theme.Highlight.Light, palette);
        SetLogo(theme.Logo);
        Applied?.Invoke(this, EventArgs.Empty);
    }

    public static Color ToColor(uint argb) =>
        Color.FromArgb((byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb);

    public static SolidColorBrush ToBrush(uint argb) => Frozen(ToColor(argb));

    /// <summary>An area color's chip text color in the current mode, or null for an unknown color id.</summary>
    public Brush? AreaBrush(string colorId) =>
        Tokens.AreaColor(colorId) is { } area ? ToBrush(IsDark ? area.Dark.Content : area.Light.Content) : null;

    /// <summary>An area color's swatch, the same in light and dark: for dots and chart bars. Null for an unknown color id.</summary>
    public Brush? SwatchBrush(string colorId) =>
        Tokens.AreaColor(colorId) is { } area ? ToBrush(area.Swatch) : null;

    /// <summary>
    /// A static face cut by tools/build_windows_fonts.py, by the name FontFaces gives it. The URI names
    /// the GoalMaker assembly, so the fonts load wherever the resources are used (tests too).
    /// </summary>
    public static FontFamily Face(string name) => new(new Uri("pack://application:,,,/GoalMaker;component/"), "./Assets/Fonts/#" + name);

    /// <summary>Whether the app ships the face FontFaces names (tools/build_windows_fonts.py cuts only some italics).</summary>
    public static bool Ships(string face) => ShippedFonts.Value.Contains("assets/fonts/" + face.Replace(' ', '-').ToLowerInvariant() + ".ttf");

    /// <summary>The room after a slanted (not real) italic word: a thin space, which scales with the text.</summary>
    public const string ItalicGap = " ";

    // The font files in the assembly's resources, by the lowercased paths WPF stores them under.
    private static readonly Lazy<HashSet<string>> ShippedFonts = new(() =>
    {
        var assembly = typeof(ThemeApplier).Assembly;
        using var stream = assembly.GetManifestResourceStream(assembly.GetName().Name + ".g.resources");
        if (stream is null)
        {
            return [];
        }

        using var reader = new System.Resources.ResourceReader(stream);
        var names = new HashSet<string>(StringComparer.Ordinal);
        var entries = reader.GetEnumerator();
        while (entries.MoveNext())
        {
            // Only the key: reading Value would load every resource in the assembly.
            if (entries.Key is string name && name.StartsWith("assets/fonts/", StringComparison.Ordinal))
            {
                names.Add(name);
            }
        }

        return names;
    });

    // The mark's colors (the logo control fades to them) and the icon rendered in them.
    private void SetLogo(LogoColors colors)
    {
        resources["GM.LogoTileColor"] = ToColor(colors.Tile);
        resources["GM.LogoLetterColor"] = ToColor(colors.Letter);
        resources["GM.LogoArrowColor"] = ToColor(colors.Arrow);
        if (logo is not null)
        {
            resources["GM.LogoMark"] = logo;
            LogoIcon = GoalMakerLogo.Render(logo, colors, 64);
            LogoIconFile = GoalMakerLogo.IconFile(logo, colors);
            resources["GM.LogoIcon"] = LogoIcon;
        }
    }

    /// <summary>
    /// The accent color keys WPF UI 4.3 reads. Each one is the theme's primary after Apply.
    /// </summary>
    public static readonly IReadOnlyList<string> AccentColorKeys =
    [
        "SystemAccentColor",
        "SystemAccentColorPrimary",
        "SystemAccentColorSecondary",
        "SystemAccentColorTertiary",
        "AccentFillColorDefault",
    ];

    /// <summary>
    /// The accent brushes WPF UI 4.3 reads: the ones its accent manager writes, and the ones its
    /// Dark.xaml and Light.xaml build once from SystemAccentColor{Primary,Secondary,Tertiary}. Each one
    /// is the theme's primary after Apply.
    /// </summary>
    public static readonly IReadOnlyList<string> AccentBrushKeys =
    [
        "SystemAccentBrush",
        "SystemFillColorAttentionBrush",
        "AccentFillColorDefaultBrush",
        "AccentFillColorSelectedTextBackgroundBrush",
        "AccentTextFillColorPrimaryBrush",
        "AccentTextFillColorSecondaryBrush",
        "AccentTextFillColorTertiaryBrush",
        "BadgeBackground",
        "CalendarViewSelectedBackground",
        "CalendarViewSelectedBorderBrush",
        "CalendarViewTodayBackground",
        "CheckBoxCheckBackgroundFillChecked",
        "ComboBoxBorderBrushFocused",
        "ComboBoxItemPillFillBrush",
        "HyperlinkButtonForeground",
        "HyperlinkButtonForegroundPointerOver",
        "HyperlinkButtonForegroundPressed",
        "InfoBarInformationalSeverityIconBackground",
        "ListBoxItemSelectedBackgroundThemeBrush",
        "ListViewItemPillFillBrush",
        "NavigationViewSelectionIndicatorForeground",
        "ProgressBarForeground",
        "ProgressRingForegroundThemeBrush",
        "RadioButtonOuterEllipseCheckedStroke",
        "RatingControlSelectedForeground",
        "SliderThumbBackground",
        "TextControlFocusedBorderBrush",
        "ThumbRateForeground",
        "ToggleButtonBackgroundChecked",
        "ToggleButtonForegroundCheckedPointerOver",
        "ToggleButtonBackgroundCheckedPressed",
        "ToggleSwitchStrokeOn",
        "ToggleSwitchFillOn",
        "TreeViewItemSelectionIndicatorForeground",
    ];

    /// <summary>The text-on-accent keys WPF UI 4.3 reads. Each one is the theme's onPrimary after Apply.</summary>
    public static readonly IReadOnlyList<string> TextOnAccentKeys =
    [
        "TextOnAccentFillColorPrimary",
        "TextOnAccentFillColorSelectedText",
        "TextOnAccentFillColorPrimaryBrush",
        "TextOnAccentFillColorSelectedTextBrush",
    ];

    // WPF UI colors its controls from these keys, and left to itself fills them from Windows' accent
    // (on its first look at the resources) or from shades it derives from the color it is given:
    // darker in light mode, lighter and greyer in dark mode, and text on them black or white by a
    // brightness guess. None of that is a GoalMaker color. Every key is the theme's primary instead,
    // with onPrimary on it. Fresh brushes under the same keys also reach controls made under the
    // last theme, because the templates look these keys up dynamically (Electric's toggles once
    // stayed Track's lime).
    private void SetAccent(Palette p)
    {
        var primary = ToColor(p.Primary);
        var onPrimary = ToColor(p.OnPrimary);
        foreach (var key in AccentColorKeys)
        {
            resources[key] = primary;
        }

        foreach (var key in AccentBrushKeys)
        {
            resources[key] = Frozen(primary);
        }

        // Hover and pressed fills: the primary a little see-through, as WPF UI makes them.
        resources["AccentFillColorSecondary"] = WithAlpha(primary, 229);
        resources["AccentFillColorTertiary"] = WithAlpha(primary, 204);
        resources["AccentFillColorSecondaryBrush"] = Frozen(WithAlpha(primary, 229));
        resources["AccentFillColorTertiaryBrush"] = Frozen(WithAlpha(primary, 204));

        foreach (var key in TextOnAccentKeys)
        {
            resources[key] = key.EndsWith("Brush", StringComparison.Ordinal) ? Frozen(onPrimary) : onPrimary;
        }

        resources["TextOnAccentFillColorSecondary"] = WithAlpha(onPrimary, 179);
        resources["TextOnAccentFillColorSecondaryBrush"] = Frozen(WithAlpha(onPrimary, 179));
    }

    private void SetColors(Palette p)
    {
        var roles = new Dictionary<string, uint>
        {
            ["Background"] = p.Background,
            ["Surface"] = p.Surface,
            ["SurfaceVariant"] = p.SurfaceVariant,
            ["Text"] = p.Text,
            ["TextMuted"] = p.TextMuted,
            ["Outline"] = p.Outline,
            ["Primary"] = p.Primary,
            ["OnPrimary"] = p.OnPrimary,
            ["Accent"] = p.Accent,
            ["OnAccent"] = p.OnAccent,
            ["Hero"] = p.Hero,
            ["OnHero"] = p.OnHero,
            ["HeroAccent"] = p.HeroAccent,
            ["Danger"] = p.Danger,
        };
        foreach (var (role, argb) in roles)
        {
            resources[$"GM.{role}Color"] = ToColor(argb);
            resources[$"GM.{role}Brush"] = ToBrush(argb);
        }

        var divider = Blend(ToColor(p.SurfaceVariant), ToColor(p.Outline), 0.5);
        var hover = Blend(ToColor(p.Background), ToColor(p.Text), 0.06);

        // WPF UI keys that its controls read directly (backgrounds, cards and inputs; the accent is SetAccent). Controls
        // with their own brush keys (CheckBoxForeground, NavigationViewItemForeground, ...) keep WPF UI's
        // neutrals, which read well on every theme; GoalMaker's views use the GM.* keys.
        resources["ApplicationBackgroundColor"] = ToColor(p.Background);
        resources["ApplicationBackgroundBrush"] = ToBrush(p.Background);
        resources["SolidBackgroundFillColorBaseBrush"] = ToBrush(p.Background);
        resources["LayerFillColorDefaultBrush"] = ToBrush(p.Background);
        resources["NavigationViewContentGridBorderBrush"] = Frozen(divider);
        resources["LeftNavigationViewSeparatorBrush"] = Frozen(divider);
        resources["TextFillColorPrimaryBrush"] = ToBrush(p.Text);
        resources["TextFillColorSecondaryBrush"] = ToBrush(p.TextMuted);
        resources["TextFillColorTertiaryBrush"] = ToBrush(p.TextMuted);
        resources["CardBackgroundFillColorDefaultBrush"] = ToBrush(p.Surface);
        resources["CardStrokeColorDefaultBrush"] = Frozen(divider);
        resources["ControlFillColorDefaultBrush"] = ToBrush(p.SurfaceVariant);
        resources["ControlFillColorSecondaryBrush"] = Frozen(Blend(ToColor(p.SurfaceVariant), ToColor(p.Outline), 0.15));
        resources["ControlStrokeColorDefaultBrush"] = Frozen(divider);
        resources["ControlStrongStrokeColorDefaultBrush"] = ToBrush(p.Outline);
        resources["TextControlBackground"] = ToBrush(p.SurfaceVariant);
        resources["TextControlBackgroundFocused"] = ToBrush(p.Surface);
        resources["SubtleFillColorSecondaryBrush"] = Frozen(hover);

        // Primary buttons (Plan tomorrow, Save, Send code): the theme's primary with its onPrimary text,
        // and hover and press as a light onPrimary state layer over it, the same in every theme (WPF UI's
        // own keys use lighter variants of the accent that don't match the theme).
        var primary = ToColor(p.Primary);
        var onPrimary = ToColor(p.OnPrimary);
        resources["AccentButtonBackground"] = ToBrush(p.Primary);
        resources["AccentButtonBackgroundPointerOver"] = Frozen(Blend(primary, onPrimary, 0.12));
        resources["AccentButtonBackgroundPressed"] = Frozen(Blend(primary, onPrimary, 0.22));
        resources["AccentButtonForeground"] = ToBrush(p.OnPrimary);
        resources["AccentButtonForegroundPointerOver"] = ToBrush(p.OnPrimary);
        resources["AccentButtonForegroundPressed"] = ToBrush(p.OnPrimary);
        resources["AccentControlElevationBorderBrush"] = Frozen(Blend(primary, onPrimary, 0.08));
        resources["SystemFillColorCriticalBrush"] = ToBrush(p.Danger);
    }

    private void SetFonts(ThemeTypography typography)
    {
        var body = typography.Body;
        var bodyFont = Face(FontFaces.Name(body.Family, body.Weight, body.Width, italic: false));
        resources["GM.BodyFont"] = bodyFont;
        resources["GM.BodyStrongFont"] = Face(FontFaces.Name(body.Family, body.StrongWeight, body.Width, italic: false));

        // *Italic* in notes and letters: the family's real italic when one ships. Without it WPF slants
        // the upright face, the slanted last letter leans over the space after it ("fourtimes"), and a
        // thin space after the word (GM.ItalicGap) gives that room back.
        var italicName = FontFaces.Name(body.Family, body.Weight, body.Width, italic: true);
        var realItalic = Ships(italicName);
        resources["GM.BodyItalicFont"] = realItalic ? Face(italicName) : bodyFont;
        resources["GM.ItalicGap"] = realItalic ? string.Empty : ItalicGap;
        resources["GM.HeadingFont"] = Face(FontFaces.Name(typography.Heading));
        resources["GM.NumberFont"] = Face(FontFaces.Name(typography.Number));
        resources["ContentControlThemeFontFamily"] = bodyFont;
    }

    private void SetShapes(ThemeShapes shapes)
    {
        resources["GM.CardCorner"] = new CornerRadius(shapes.Card);
        resources["GM.RowCorner"] = new CornerRadius(shapes.Row);
        resources["GM.CheckboxCorner"] = new CornerRadius(shapes.Checkbox);
        resources["GM.ButtonCorner"] = new CornerRadius(shapes.Button);
        // Chips are about 28 px tall: 14 makes a pill, where WPF would draw 999 as an ellipse.
        resources["GM.ChipCorner"] = new CornerRadius(Math.Min(shapes.Button, 14));
        // WPF UI draws checkboxes, buttons and inputs with one radius; the checkbox's keeps Track square
        // and stops a small box from turning into a circle.
        resources["ControlCornerRadius"] = new CornerRadius(Math.Min(shapes.Checkbox, 6));
        resources["OverlayCornerRadius"] = new CornerRadius(Math.Min(shapes.Card, 12));
    }

    private void SetDensity(Density density)
    {
        resources["GM.PagePadding"] = new Thickness(density.PagePadding);
        // A page whose right side runs to the window edge, so its scrollbar sits at the edge
        // rather than floating in the padding, and the gutter the content keeps clear of it.
        resources["GM.PagePaddingToEdge"] = new Thickness(density.PagePadding, density.PagePadding, 0, density.PagePadding);
        resources["GM.PageGutter"] = new Thickness(0, 0, density.PagePadding, 0);
        // The bottom bar under a page that scrolls: the page's sides and foot, nothing above it.
        resources["GM.BarMargin"] = new Thickness(density.PagePadding, 0, density.PagePadding, density.PagePadding);
        resources["GM.CardPadding"] = new Thickness(density.CardPadding);
        resources["GM.RowGap"] = new Thickness(0, 0, 0, density.RowGap);
        resources["GM.RowMinHeight"] = (double)density.RowMinHeight;
    }

    // The durations charts and panels move with (docs/design/spec.md, "Motion and feedback").
    private void SetMotion(MotionTokens motion)
    {
        resources["GM.QuickDuration"] = new Duration(TimeSpan.FromMilliseconds(motion.Quick));
        resources["GM.StandardDuration"] = new Duration(TimeSpan.FromMilliseconds(motion.Standard));
    }

    /// <summary>
    /// The Settings highlight (the kit's Tray.Highlight* brushes) from the theme's own tokens in
    /// themes.json, which tools/check_design_tokens.py holds to 4.5 to 1 for text and the title on the
    /// tinted card. The kit derives the same brushes from the spot in the palette, but its title rule
    /// asks only 3 to 1 (Electric's dark title would be the spot at 4.0 to 1), so GoalMaker sets its
    /// tokens over them. In a Windows high contrast theme the kit's system colors stay.
    /// </summary>
    private void SetHighlight(HighlightTokens highlight, Palette palette)
    {
        if (SystemParameters.HighContrast)
        {
            return;
        }

        var spot = ToColor(highlight.Spot);
        resources[TrayThemeTokens.HighlightSpot] = Frozen(spot);
        resources[TrayThemeTokens.HighlightTint] = Frozen(Blend(ToColor(palette.Surface), spot, highlight.Tint));
        resources[TrayThemeTokens.HighlightRing] = Frozen(WithAlpha(ToColor(highlight.Ring), Alpha(highlight.RingAlpha)));
        resources[TrayThemeTokens.HighlightGlow] = Frozen(WithAlpha(spot, Alpha(highlight.GlowAlpha)));
        resources[TrayThemeTokens.HighlightEdge] = ToBrush(highlight.Edge);
        resources[TrayThemeTokens.HighlightTitle] = ToBrush(highlight.Title);
    }

    private static byte Alpha(double amount) => (byte)Math.Round(Math.Clamp(amount, 0, 1) * 255);

    private static SolidColorBrush Frozen(Color color)
    {
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    private static Color WithAlpha(Color color, byte alpha) => Color.FromArgb(alpha, color.R, color.G, color.B);

    private static Color Blend(Color from, Color to, double amount) => Color.FromRgb(
        (byte)Math.Round(from.R + ((to.R - from.R) * amount)),
        (byte)Math.Round(from.G + ((to.G - from.G) * amount)),
        (byte)Math.Round(from.B + ((to.B - from.B) * amount)));
}

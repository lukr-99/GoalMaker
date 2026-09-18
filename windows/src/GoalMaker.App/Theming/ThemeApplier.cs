using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using GoalMaker.Core.Design;
using GoalMaker.Core.Settings;
using Microsoft.Win32;
using Wpf.Ui.Appearance;
using Wpf.Ui.Controls;
using ThemeMode = GoalMaker.Core.Settings.ThemeMode;

namespace GoalMaker.App.Theming;

/// <summary>
/// Applies the chosen theme from contracts/design/themes.json (ADR 0008) in light, dark or pure
/// black: GoalMaker's own resources (GM.* brushes, fonts, corners, spacing), and WPF UI's theme and
/// the resource keys its controls use, so built-in controls match. Views read GM.* keys through
/// DynamicResource, so switching applies at once. Follows Windows' light or dark when the mode is
/// System.
/// </summary>
public sealed class ThemeApplier : IDisposable
{
    private readonly ResourceDictionary resources;
    private Appearance current = Appearance.Default;

    public ThemeApplier(DesignTokens tokens, ResourceDictionary resources)
    {
        Tokens = tokens;
        this.resources = resources;
        SystemEvents.UserPreferenceChanged += OnUserPreferenceChanged;
    }

    public DesignTokens Tokens { get; }

    /// <summary>Whether the last applied appearance came out dark (for previews of the other themes).</summary>
    public bool IsDark { get; private set; }

    /// <summary>Whether animations should become short fades (the system setting or the in-app switch).</summary>
    public bool MotionReduced { get; private set; }

    /// <summary>After every Apply, so views can rebuild what they colored in code (area dots).</summary>
    public event EventHandler? Applied;

    /// <summary>
    /// The body font becomes the window's default. The window's background stays WPF UI's (it sets it
    /// in code); MainWindow's root grid paints GM.BackgroundBrush over it, title bar included.
    /// </summary>
    public void Attach(Window target) => target.SetResourceReference(Control.FontFamilyProperty, "GM.BodyFont");

    public void Apply(Appearance appearance)
    {
        current = appearance;
        IsDark = appearance.Mode switch
        {
            ThemeMode.Light => false,
            ThemeMode.Dark => true,
            _ => SystemIsDark(),
        };
        var theme = Tokens.Theme(appearance.ThemeId);
        var palette = !IsDark ? theme.Light : appearance.PureBlack ? theme.Black : theme.Dark;
        var wpfTheme = IsDark ? ApplicationTheme.Dark : ApplicationTheme.Light;

        ApplicationThemeManager.Apply(wpfTheme, WindowBackdropType.None, false);
        ApplicationAccentColorManager.Apply(ToColor(palette.Primary), wpfTheme, false, false);

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
        Applied?.Invoke(this, EventArgs.Empty);
    }

    public void Dispose() => SystemEvents.UserPreferenceChanged -= OnUserPreferenceChanged;


    public static Color ToColor(uint argb) =>
        Color.FromArgb((byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb);

    public static SolidColorBrush ToBrush(uint argb) => Frozen(ToColor(argb));

    /// <summary>An area color's chip text color in the current mode, or null for an unknown color id.</summary>
    public Brush? AreaBrush(string colorId) =>
        Tokens.AreaColor(colorId) is { } area ? ToBrush(IsDark ? area.Dark.Content : area.Light.Content) : null;

    /// <summary>
    /// A static face cut by tools/build_windows_fonts.py, by the name FontFaces gives it. The URI names
    /// the GoalMaker assembly, so the fonts load wherever the resources are used (tests too).
    /// </summary>
    public static FontFamily Face(string name) => new(new Uri("pack://application:,,,/GoalMaker;component/"), "./Assets/Fonts/#" + name);

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

        // WPF UI keys that its controls read directly (the accent, backgrounds, cards and inputs). Controls
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
        resources["AccentFillColorDefaultBrush"] = ToBrush(p.Primary);
        resources["TextOnAccentFillColorPrimaryBrush"] = ToBrush(p.OnPrimary);
        resources["AccentTextFillColorPrimaryBrush"] = ToBrush(p.Primary);
        resources["SystemFillColorCriticalBrush"] = ToBrush(p.Danger);
    }

    private void SetFonts(ThemeTypography typography)
    {
        var body = typography.Body;
        var bodyFont = Face(FontFaces.Name(body.Family, body.Weight, body.Width, italic: false));
        resources["GM.BodyFont"] = bodyFont;
        resources["GM.BodyStrongFont"] = Face(FontFaces.Name(body.Family, body.StrongWeight, body.Width, italic: false));
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
        resources["GM.CardPadding"] = new Thickness(density.CardPadding);
        resources["GM.RowGap"] = new Thickness(0, 0, 0, density.RowGap);
        resources["GM.RowMinHeight"] = (double)density.RowMinHeight;
    }

    private void OnUserPreferenceChanged(object sender, UserPreferenceChangedEventArgs e)
    {
        if (current.Mode == ThemeMode.System && e.Category is UserPreferenceCategory.General or UserPreferenceCategory.Color)
        {
            Application.Current?.Dispatcher.BeginInvoke(() => Apply(current));
        }
    }

    private static SolidColorBrush Frozen(Color color)
    {
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    private static Color Blend(Color from, Color to, double amount) => Color.FromRgb(
        (byte)Math.Round(from.R + ((to.R - from.R) * amount)),
        (byte)Math.Round(from.G + ((to.G - from.G) * amount)),
        (byte)Math.Round(from.B + ((to.B - from.B) * amount)));

    private static bool SystemIsDark() =>
        ApplicationThemeManager.GetSystemTheme() is SystemTheme.Dark or SystemTheme.Glow or SystemTheme.CapturedMotion
            or SystemTheme.HCBlack;
}

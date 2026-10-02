using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using DotNetLib.Tray;
using GoalMaker.App.Theming;
using GoalMaker.Core.Settings;
using GoalMaker.Infrastructure.Sync;
using Wpf.Ui.Appearance;
using Wpf.Ui.Markup;
using Appearance = GoalMaker.Core.Settings.Appearance;
using ThemeMode = GoalMaker.Core.Settings.ThemeMode;
using WpfUiButton = Wpf.Ui.Controls.Button;

namespace GoalMaker.App.Tests;

/// <summary>
/// WPF UI colors its primary buttons, toggles, checks and selections from its accent keys, which it
/// fills from Windows' accent or from shades of its own. Every one has to be the theme's primary, with
/// onPrimary on it, in every theme and mode, and again after a switch.
/// </summary>
public sealed class ThemeApplierTests
{
    // Windows' accent as WPF UI writes it before GoalMaker applies a theme: a violet that is no theme's primary.
    private static readonly Color WindowsAccent = Color.FromRgb(0x77, 0x3A, 0xDB);

    public static TheoryData<string, ThemeMode, bool> Looks()
    {
        var looks = new TheoryData<string, ThemeMode, bool>();
        foreach (var theme in ContractResources.Themes().Themes)
        {
            looks.Add(theme.Id, ThemeMode.Light, false);
            looks.Add(theme.Id, ThemeMode.Dark, false);
            looks.Add(theme.Id, ThemeMode.Dark, true);
        }

        return looks;
    }

    [Theory]
    [MemberData(nameof(Looks))]
    public void EveryWpfUiAccentKeyIsTheThemesPrimary(string id, ThemeMode mode, bool pureBlack) => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources);
        var (primary, onPrimary) = Pair(theme, id, mode, pureBlack);

        theme.Apply(Appearance.Default with { ThemeId = id, Mode = mode, PureBlack = pureBlack });

        Assert.All(ThemeApplier.AccentColorKeys, key => Assert.Equal(primary, (Color)resources[key]));
        Assert.All(ThemeApplier.AccentBrushKeys, key => Assert.Equal(primary, ((SolidColorBrush)resources[key]).Color));
        Assert.All(ThemeApplier.TextOnAccentKeys, key => Assert.Equal(onPrimary, ColorOf(resources[key])));
        Assert.Equal(primary, ((SolidColorBrush)resources["AccentButtonBackground"]).Color);
        Assert.Equal(onPrimary, ((SolidColorBrush)resources["AccentButtonForeground"]).Color);
    });

    [Theory]
    [MemberData(nameof(Looks))]
    public void APrimaryButtonMadeUnderAnotherThemeTakesThisOnesPrimary(string id, ThemeMode mode, bool pureBlack) => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources);
        var other = id == "electric" ? "sunrise" : "electric";
        theme.Apply(Appearance.Default with { ThemeId = other, Mode = ThemeMode.Light });
        var button = new WpfUiButton { Appearance = Wpf.Ui.Controls.ControlAppearance.Primary, Content = "Plan tomorrow" };
        var host = new Border { Resources = resources, Child = button };
        Lay(host);

        theme.Apply(Appearance.Default with { ThemeId = id, Mode = mode, PureBlack = pureBlack });
        Lay(host);

        var (primary, onPrimary) = Pair(theme, id, mode, pureBlack);
        Assert.Equal(primary, ((SolidColorBrush)button.Background).Color);
        Assert.Equal(onPrimary, ((SolidColorBrush)button.Foreground).Color);
    });

    // The tray kit's applier sets the Tray.* brushes its dialogs and Window style read; GoalMaker
    // gives it the theme's palettes, so they match the GM.* brushes.
    [Theory]
    [MemberData(nameof(Looks))]
    public void TheKitsBrushesWearTheThemesColors(string id, ThemeMode mode, bool pureBlack) => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources);

        theme.Apply(Appearance.Default with { ThemeId = id, Mode = mode, PureBlack = pureBlack });

        var definition = theme.Tokens.Theme(id);
        var palette = mode == ThemeMode.Light ? definition.Light : pureBlack ? definition.Black : definition.Dark;
        Assert.Equal(ThemeApplier.ToColor(palette.Background), BrushColor(resources, TrayThemeTokens.Background));
        Assert.Equal(ThemeApplier.ToColor(palette.Text), BrushColor(resources, TrayThemeTokens.TextPrimary));
        Assert.Equal(ThemeApplier.ToColor(palette.Primary), BrushColor(resources, TrayThemeTokens.Primary));
        Assert.Equal(BrushColor(resources, "GM.BackgroundBrush"), BrushColor(resources, TrayThemeTokens.Background));
    });

    // The Settings highlight is the theme's own tokens (themes.json, "highlight"), not the kit's
    // derivation, so text and the title keep 4.5 to 1 on the lit card as tools/check_design_tokens.py checks.
    [Theory]
    [MemberData(nameof(Looks))]
    public void TheSettingsHighlightIsTheThemesOwnTokens(string id, ThemeMode mode, bool pureBlack) => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources);

        theme.Apply(Appearance.Default with { ThemeId = id, Mode = mode, PureBlack = pureBlack });

        var definition = theme.Tokens.Theme(id);
        var palette = mode == ThemeMode.Light ? definition.Light : pureBlack ? definition.Black : definition.Dark;
        var tokens = mode == ThemeMode.Light ? definition.Highlight.Light : definition.Highlight.Dark;
        var tint = BrushColor(resources, TrayThemeTokens.HighlightTint);
        Assert.Equal(ThemeApplier.ToColor(tokens.Spot), BrushColor(resources, TrayThemeTokens.HighlightSpot));
        Assert.Equal(ThemeApplier.ToColor(tokens.Title), BrushColor(resources, TrayThemeTokens.HighlightTitle));
        Assert.Equal(ThemeApplier.ToColor(tokens.Edge), BrushColor(resources, TrayThemeTokens.HighlightEdge));
        Assert.Equal((byte)Math.Round(tokens.GlowAlpha * 255), BrushColor(resources, TrayThemeTokens.HighlightGlow).A);
        Assert.True(Contrast(ThemeApplier.ToColor(tokens.Title), tint) >= 4.5, $"{id} {mode}: the title on the tint {tint}");
        Assert.True(Contrast(ThemeApplier.ToColor(palette.Text), tint) >= 4.5, $"{id} {mode}: text on the tint {tint}");
    });

    // The Settings page title and its cards follow the theme: its heading face and its card corner
    // (Electric 24, Track 12), which the kit's tint, ring and glow follow too.
    [Theory]
    [InlineData("track", 12)]
    [InlineData("electric", 24)]
    [InlineData("night", 10)]
    [InlineData("sunrise", 28)]
    public void TheSettingsPageTakesTheThemesHeadingFontAndCardCorner(string id, double corner) => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources);

        theme.Apply(Appearance.Default with { ThemeId = id, Mode = ThemeMode.Light });

        Assert.Same(resources["GM.HeadingFont"], resources[SettingsStyles.HeadingFontKey]);
        Assert.Equal(new CornerRadius(corner), resources[SettingsStyles.CardCornerRadiusKey]);
        Assert.Equal(new CornerRadius(theme.Tokens.Theme(id).Shapes.Card), resources[SettingsStyles.CardCornerRadiusKey]);
    });

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void SystemModeFollowsWindowsLightOrDark(bool windowsIsDark) => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources, systemIsDark: () => windowsIsDark);

        theme.Apply(Appearance.Default with { ThemeId = "electric", Mode = ThemeMode.System });

        var definition = theme.Tokens.Theme("electric");
        var expected = windowsIsDark ? definition.Dark : definition.Light;
        Assert.Equal(windowsIsDark, theme.IsDark);
        Assert.Equal(ThemeApplier.ToColor(expected.Background), BrushColor(resources, "GM.BackgroundBrush"));
        Assert.Equal(ThemeApplier.ToColor(expected.Background), BrushColor(resources, TrayThemeTokens.Background));
    });

    [Fact]
    public void ASwitchToAnotherThemeOrToPureBlackRepaintsTheKitsBrushes() => OnStaThread(() =>
    {
        var resources = WpfUiResources();
        using var theme = new ThemeApplier(ContractResources.Themes(), resources);
        var applied = 0;
        theme.Applied += (_, _) => applied++;

        theme.Apply(Appearance.Default with { ThemeId = "electric", Mode = ThemeMode.Dark });
        theme.Apply(Appearance.Default with { ThemeId = "sunrise", Mode = ThemeMode.Dark });
        Assert.Equal(ThemeApplier.ToColor(theme.Tokens.Theme("sunrise").Dark.Background), BrushColor(resources, TrayThemeTokens.Background));

        theme.Apply(Appearance.Default with { ThemeId = "sunrise", Mode = ThemeMode.Dark, PureBlack = true });
        Assert.Equal(ThemeApplier.ToColor(theme.Tokens.Theme("sunrise").Black.Background), BrushColor(resources, TrayThemeTokens.Background));
        Assert.Equal(3, applied);
    });

    [Theory]
    [InlineData(ThemeMode.System, TrayThemeMode.System)]
    [InlineData(ThemeMode.Light, TrayThemeMode.Light)]
    [InlineData(ThemeMode.Dark, TrayThemeMode.Dark)]
    public void EverySavedModeHasTheKitsMode(ThemeMode mode, TrayThemeMode expected) =>
        Assert.Equal(expected, ThemeApplier.ToTrayMode(mode));

    [Fact]
    public void TheAccentKeysCoverWhatWpfUiWritesForItsAccent() => OnStaThread(() =>
    {
        var keys = ThemeApplier.AccentColorKeys.Concat(ThemeApplier.AccentBrushKeys).Concat(ThemeApplier.TextOnAccentKeys).ToHashSet();

        // What ApplicationAccentColorManager.Apply sets, minus the see-through and disabled variants.
        Assert.Superset(new HashSet<string>
        {
            "SystemAccentColor", "SystemAccentColorPrimary", "SystemAccentColorSecondary", "SystemAccentColorTertiary",
            "SystemAccentBrush", "SystemFillColorAttentionBrush", "AccentTextFillColorPrimaryBrush", "AccentTextFillColorSecondaryBrush",
            "AccentTextFillColorTertiaryBrush", "AccentFillColorSelectedTextBackgroundBrush", "AccentFillColorDefault",
            "AccentFillColorDefaultBrush", "TextOnAccentFillColorPrimary", "TextOnAccentFillColorSelectedText",
        }, keys);
    });

    // The WPF UI dictionaries the tray kit merges (Theming/AppResources), with Windows' accent already written into them.
    private static ResourceDictionary WpfUiResources()
    {
        // WPF UI finds its dictionaries by pack URIs, which Application's static constructor registers.
        _ = Application.Current;
        var resources = new ResourceDictionary();
        resources.MergedDictionaries.Add(new ThemesDictionary { Theme = ApplicationTheme.Light });
        resources.MergedDictionaries.Add(new ControlsDictionary());
        foreach (var key in ThemeApplier.AccentColorKeys)
        {
            resources[key] = WindowsAccent;
        }

        foreach (var key in ThemeApplier.AccentBrushKeys.Append("AccentButtonBackground"))
        {
            resources[key] = new SolidColorBrush(WindowsAccent);
        }

        return resources;
    }

    private static (Color Primary, Color OnPrimary) Pair(ThemeApplier theme, string id, ThemeMode mode, bool pureBlack)
    {
        var definition = theme.Tokens.Theme(id);
        var palette = mode == ThemeMode.Light ? definition.Light : pureBlack ? definition.Black : definition.Dark;
        return (ThemeApplier.ToColor(palette.Primary), ThemeApplier.ToColor(palette.OnPrimary));
    }

    private static Color BrushColor(ResourceDictionary resources, string key) => ((SolidColorBrush)resources[key]).Color;

    private static Color ColorOf(object value) => value is SolidColorBrush brush ? brush.Color : (Color)value;

    private static double Contrast(Color a, Color b)
    {
        static double Luminance(Color color)
        {
            static double Channel(byte value)
            {
                var c = value / 255.0;
                return c <= 0.03928 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4);
            }

            return (0.2126 * Channel(color.R)) + (0.7152 * Channel(color.G)) + (0.0722 * Channel(color.B));
        }

        var (x, y) = (Luminance(a), Luminance(b));
        return (Math.Max(x, y) + 0.05) / (Math.Min(x, y) + 0.05);
    }

    private static void Lay(FrameworkElement element)
    {
        element.Measure(new Size(400, 100));
        element.Arrange(new Rect(0, 0, 400, 100));
        element.UpdateLayout();
    }

    private static void OnStaThread(Action test)
    {
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                test();
            }
            catch (Exception exception)
            {
                failure = exception;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null)
        {
            throw new InvalidOperationException("The test failed on its STA thread", failure);
        }
    }
}

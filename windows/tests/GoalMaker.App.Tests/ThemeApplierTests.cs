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

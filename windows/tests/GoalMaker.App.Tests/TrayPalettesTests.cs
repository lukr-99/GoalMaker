using System.Windows.Media;
using GoalMaker.App.Theming;
using GoalMaker.Core.Design;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>
/// The tray kit's brushes wear each theme's colors (CodePrint: every tray app checks its palettes
/// against WCAG AA in a test): text 4.5 to 1 on every surface, status and focus 3 to 1 on the background.
/// </summary>
public sealed class TrayPalettesTests
{
    public static TheoryData<string, string> Palettes()
    {
        var palettes = new TheoryData<string, string>();
        foreach (var theme in ContractResources.Themes().Themes)
        {
            palettes.Add(theme.Id, "light");
            palettes.Add(theme.Id, "dark");
            palettes.Add(theme.Id, "black");
        }

        return palettes;
    }

    [Theory]
    [MemberData(nameof(Palettes))]
    public void TextIsReadableOnEverySurface(string id, string mode)
    {
        var palette = TrayPalettes.From(PaletteOf(id, mode));

        foreach (var surface in new[] { palette.Background, palette.Surface, palette.SurfaceRaised })
        {
            Assert.True(Contrast(palette.TextPrimary, surface) >= 4.5, $"{id} {mode}: text on {surface}");
            Assert.True(Contrast(palette.TextSecondary, surface) >= 4.5, $"{id} {mode}: secondary text on {surface}");
        }

        Assert.True(Contrast(palette.OnPrimary, palette.Primary) >= 4.5, $"{id} {mode}: onPrimary on primary");
    }

    [Theory]
    [MemberData(nameof(Palettes))]
    public void StatusAndFocusStandOutFromTheBackground(string id, string mode)
    {
        var palette = TrayPalettes.From(PaletteOf(id, mode));

        foreach (var color in new[] { palette.Danger, palette.Success, palette.Warning, palette.Focus })
        {
            Assert.True(Contrast(color, palette.Background) >= 3, $"{id} {mode}: {color} on the background");
        }
    }

    [Fact]
    public void EachKitRoleIsTheMatchingGoalMakerRole()
    {
        var source = ContractResources.Themes().Theme(null).Light;

        var palette = TrayPalettes.From(source);

        Assert.Equal(ThemeApplier.ToColor(source.Background), palette.Background);
        Assert.Equal(ThemeApplier.ToColor(source.SurfaceVariant), palette.SurfaceRaised);
        Assert.Equal(ThemeApplier.ToColor(source.Text), palette.TextPrimary);
        Assert.Equal(ThemeApplier.ToColor(source.TextMuted), palette.TextSecondary);
        Assert.Equal(ThemeApplier.ToColor(source.Outline), palette.Border);
        Assert.Equal(ThemeApplier.ToColor(source.Accent), palette.Success);
        Assert.Equal(ThemeApplier.ToColor(source.Danger), palette.Warning);
    }

    [Fact]
    public void PureBlackSwapsOnlyTheDarkPalette()
    {
        var theme = ContractResources.Themes().Theme(null);

        var (light, black) = TrayPalettes.For(theme, pureBlack: true);
        var (sameLight, dark) = TrayPalettes.For(theme, pureBlack: false);

        Assert.Equal(sameLight, light);
        Assert.Equal(TrayPalettes.From(theme.Black), black);
        Assert.Equal(TrayPalettes.From(theme.Dark), dark);
    }

    private static Palette PaletteOf(string id, string mode)
    {
        var theme = ContractResources.Themes().Theme(id);
        return mode switch
        {
            "light" => theme.Light,
            "dark" => theme.Dark,
            _ => theme.Black,
        };
    }

    private static double Contrast(Color a, Color b)
    {
        var (x, y) = (Luminance(a), Luminance(b));
        var (lighter, darker) = x > y ? (x, y) : (y, x);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double Luminance(Color color)
    {
        static double Channel(byte value)
        {
            var c = value / 255.0;
            return c <= 0.03928 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4);
        }

        return (0.2126 * Channel(color.R)) + (0.7152 * Channel(color.G)) + (0.0722 * Channel(color.B));
    }
}

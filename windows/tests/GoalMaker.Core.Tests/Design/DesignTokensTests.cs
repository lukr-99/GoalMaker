using GoalMaker.Core.Design;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.Core.Tests.Design;

public sealed class DesignTokensTests
{
    private readonly DesignTokens tokens = ContractResources.Themes();

    [Fact]
    public void TheFourThemesLoadWithTrackAsTheDefault()
    {
        Assert.Equal(["track", "electric", "night", "sunrise"], tokens.Themes.Select(theme => theme.Id));
        Assert.Equal("track", tokens.Theme(null).Id);
        Assert.Equal("track", tokens.Theme("retired-theme").Id);
        Assert.Equal("night", tokens.Theme("night").Id);
    }

    [Fact]
    public void ColorsBecomeOpaqueArgb()
    {
        Assert.Equal(0xFFD6FF3Au, tokens.Theme("track").Dark.Primary);
        Assert.Equal(0xFFFFFFFFu, tokens.Theme("track").Light.Background);
    }

    [Fact]
    public void PureBlackKeepsTheDarkPaletteOnBlackSurfaces()
    {
        foreach (var theme in tokens.Themes)
        {
            Assert.Equal(0xFF000000u, theme.Black.Background);
            Assert.Equal(theme.Dark.Text, theme.Black.Text);
            Assert.Equal(theme.Dark.Primary, theme.Black.Primary);
        }
    }

    [Fact]
    public void WindowsUsesTheCompactSpacing()
    {
        Assert.Equal(36, tokens.Density.RowMinHeight);
        Assert.Equal(4, tokens.Density.RowGap);
    }

    [Fact]
    public void FaceNamesMatchTheFontBuilder()
    {
        Assert.Equal("GoalMaker Archivo 900 Wide Italic", FontFaces.Name(tokens.Theme("track").Typography.Heading));
        Assert.Equal("GoalMaker Plus Jakarta Sans 800", FontFaces.Name(tokens.Theme("electric").Typography.Heading));
        Assert.Throws<ArgumentOutOfRangeException>(() => FontFaces.Name("Archivo", 400, 75, italic: false));
    }

    [Fact]
    public void EveryFaceAThemeAsksForIsBuiltIn()
    {
        var folder = new DirectoryInfo(AppContext.BaseDirectory);
        while (!Directory.Exists(Path.Combine(folder!.FullName, "windows", "src", "GoalMaker.App", "Assets", "Fonts")))
        {
            folder = folder.Parent;
        }

        var fonts = Path.Combine(folder.FullName, "windows", "src", "GoalMaker.App", "Assets", "Fonts");
        foreach (var theme in tokens.Themes)
        {
            var body = theme.Typography.Body;
            string[] faces =
            [
                FontFaces.Name(theme.Typography.Heading),
                FontFaces.Name(theme.Typography.Number),
                FontFaces.Name(body.Family, body.Weight, body.Width, italic: false),
                FontFaces.Name(body.Family, body.StrongWeight, body.Width, italic: false),
            ];
            foreach (var face in faces)
            {
                Assert.True(File.Exists(Path.Combine(fonts, face.Replace(' ', '-') + ".ttf")), $"{theme.Id} needs {face}");
            }
        }
    }
}

using System.Globalization;
using System.Windows.Media;
using GoalMaker.App.Theming;
using GoalMaker.Core.Design;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One theme card in Settings, drawn in that theme's colors and fonts for the current mode. The
/// Theme row's choice cards pick one; the name is what a screen reader and the search use.
/// </summary>
public sealed class ThemeOptionViewModel
{
    public ThemeOptionViewModel(ThemeDefinition theme, bool dark)
    {
        var palette = dark ? theme.Dark : theme.Light;
        Id = theme.Id;
        Name = theme.Typography.Heading.Uppercase ? theme.Name.ToUpper(CultureInfo.CurrentUICulture) : theme.Name;
        AccessibleName = theme.Name;
        Summary = theme.Summary;
        Background = ThemeApplier.ToBrush(palette.Background);
        Text = ThemeApplier.ToBrush(palette.Text);
        Hero = ThemeApplier.ToBrush(palette.Hero);
        HeroAccent = ThemeApplier.ToBrush(palette.HeroAccent);
        Accent = ThemeApplier.ToBrush(palette.Accent);
        Primary = ThemeApplier.ToBrush(palette.Primary);
        HeadingFont = ThemeApplier.Face(FontFaces.Name(theme.Typography.Heading));
        NumberFont = ThemeApplier.Face(FontFaces.Name(theme.Typography.Number));
    }

    public string Id { get; }

    public string Name { get; }

    public string AccessibleName { get; }

    public string Summary { get; }

    public Brush Background { get; }

    public Brush Text { get; }

    public Brush Hero { get; }

    public Brush HeroAccent { get; }

    public Brush Accent { get; }

    public Brush Primary { get; }

    public FontFamily HeadingFont { get; }

    public FontFamily NumberFont { get; }

    public override string ToString() => AccessibleName;
}

using System.Globalization;
using System.Windows.Media;
using GoalMaker.App.Theming;
using GoalMaker.Core.Design;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One theme card in Settings, drawn in that theme's colors and fonts for the current mode. Checking
/// the card (click, keyboard or a screen reader's select) chooses the theme.
/// </summary>
public sealed class ThemeOptionViewModel
{
    private readonly Action<string> select;
    private readonly bool selected;

    public ThemeOptionViewModel(ThemeDefinition theme, bool dark, bool selected, Action<string> select)
    {
        this.select = select;
        this.selected = selected;
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

    public bool IsSelected
    {
        get => selected;
        set
        {
            if (value && !selected)
            {
                select(Id);
            }
        }
    }

    public Brush Background { get; }

    public Brush Text { get; }

    public Brush Hero { get; }

    public Brush HeroAccent { get; }

    public Brush Accent { get; }

    public Brush Primary { get; }

    public FontFamily HeadingFont { get; }

    public FontFamily NumberFont { get; }
}

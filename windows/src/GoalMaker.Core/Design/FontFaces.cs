using System.Globalization;

namespace GoalMaker.Core.Design;

/// <summary>
/// The family name of each static face tools/build_windows_fonts.py cuts for WPF, which can't choose a
/// variable font's weight or width: "GoalMaker Archivo 900 Wide Italic". Both must agree.
/// </summary>
public static class FontFaces
{
    public const double NormalWidth = 100;
    public const double WideWidth = 112.5;

    public static string Name(string family, int weight, double width, bool italic)
    {
        if (width is not (NormalWidth or WideWidth))
        {
            throw new ArgumentOutOfRangeException(nameof(width), width, "Widths are normal (100) or wide (112.5).");
        }

        return string.Create(CultureInfo.InvariantCulture, $"GoalMaker {family} {weight}")
            + (width == WideWidth ? " Wide" : string.Empty)
            + (italic ? " Italic" : string.Empty);
    }

    public static string Name(TypeStyle style) => Name(style.Family, style.Weight, style.Width, style.Italic);
}

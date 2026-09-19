using System.Globalization;
using System.Text.Json;

namespace GoalMaker.Core.Design;

/// <summary>
/// Everything contracts/design/themes.json defines, as the Windows app uses it (ADR 0008). The file is
/// checked by tools/check_design_tokens.py, so loading trusts its shape and fails loudly otherwise.
/// </summary>
public sealed class DesignTokens
{
    private readonly Dictionary<string, ThemeDefinition> byId;

    public DesignTokens(string defaultThemeId, IReadOnlyList<ThemeDefinition> themes, IReadOnlyList<AreaColor> areaColors, Density density, MotionTokens motion)
    {
        DefaultThemeId = defaultThemeId;
        Themes = themes;
        AreaColors = areaColors;
        Density = density;
        Motion = motion;
        byId = themes.ToDictionary(theme => theme.Id, StringComparer.Ordinal);
    }

    public string DefaultThemeId { get; }

    public IReadOnlyList<ThemeDefinition> Themes { get; }

    public IReadOnlyList<AreaColor> AreaColors { get; }

    public Density Density { get; }

    public MotionTokens Motion { get; }

    /// <summary>The theme with <paramref name="id"/>, or the default when it's unknown (for example a retired theme).</summary>
    public ThemeDefinition Theme(string? id) =>
        id is not null && byId.TryGetValue(id, out var theme) ? theme : byId[DefaultThemeId];

    public AreaColor? AreaColor(string id) => AreaColors.FirstOrDefault(area => area.Id == id);

    public static DesignTokens Load(Stream json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;
        var themes = root.GetProperty("themes").EnumerateObject().Select(theme => Theme(theme.Name, theme.Value)).ToList();
        var areas = root.GetProperty("areaPalette").GetProperty("colors").EnumerateArray()
            .Select(area => new AreaColor(
                area.GetProperty("id").GetString()!,
                ParseColor(area.GetProperty("swatch").GetString()!),
                Chip(area.GetProperty("light")),
                Chip(area.GetProperty("dark"))))
            .ToList();
        var windows = root.GetProperty("density").GetProperty("windows");
        var motion = root.GetProperty("motion");
        return new DesignTokens(
            root.GetProperty("defaultTheme").GetString()!,
            themes,
            areas,
            new Density(
                windows.GetProperty("rowMinHeight").GetInt32(),
                windows.GetProperty("rowGap").GetInt32(),
                windows.GetProperty("pagePadding").GetInt32(),
                windows.GetProperty("cardPadding").GetInt32()),
            new MotionTokens(
                motion.GetProperty("quick").GetInt32(),
                motion.GetProperty("standard").GetInt32(),
                motion.GetProperty("emphasized").GetInt32()));
    }

    /// <summary>"#RRGGBB" as an opaque ARGB value.</summary>
    public static uint ParseColor(string hex)
    {
        if (hex.Length != 7 || hex[0] != '#')
        {
            throw new FormatException($"Not a #RRGGBB color: {hex}");
        }

        return 0xFF000000u | uint.Parse(hex.AsSpan(1), NumberStyles.HexNumber, CultureInfo.InvariantCulture);
    }

    private static ThemeDefinition Theme(string id, JsonElement theme)
    {
        var typography = theme.GetProperty("typography");
        var shape = theme.GetProperty("shape");
        var dark = theme.GetProperty("dark");
        var black = theme.GetProperty("black");
        var body = typography.GetProperty("body");
        var logo = theme.GetProperty("logo");
        return new ThemeDefinition(
            id,
            theme.GetProperty("name").GetString()!,
            theme.GetProperty("summary").GetString()!,
            new ThemeTypography(
                Style(typography.GetProperty("heading")),
                new BodyStyle(
                    body.GetProperty("family").GetString()!,
                    body.GetProperty("weight").GetInt32(),
                    body.GetProperty("strongWeight").GetInt32(),
                    body.GetProperty("width").GetDouble()),
                Style(typography.GetProperty("number"))),
            new ThemeShapes(
                shape.GetProperty("card").GetInt32(),
                shape.GetProperty("row").GetInt32(),
                shape.GetProperty("checkbox").GetInt32(),
                shape.GetProperty("button").GetInt32()),
            Palette(role => theme.GetProperty("light").GetProperty(role).GetString()!),
            Palette(role => dark.GetProperty(role).GetString()!),
            Palette(role => (black.TryGetProperty(role, out var over) ? over : dark.GetProperty(role)).GetString()!),
            new LogoColors(
                ParseColor(logo.GetProperty("tile").GetString()!),
                ParseColor(logo.GetProperty("letter").GetString()!),
                ParseColor(logo.GetProperty("arrow").GetString()!)));
    }

    private static TypeStyle Style(JsonElement style) => new(
        style.GetProperty("family").GetString()!,
        style.GetProperty("weight").GetInt32(),
        style.GetProperty("width").GetDouble(),
        style.TryGetProperty("italic", out var italic) && italic.GetBoolean(),
        style.TryGetProperty("uppercase", out var uppercase) && uppercase.GetBoolean(),
        style.TryGetProperty("tracking", out var tracking) ? tracking.GetDouble() : 0);

    private static Palette Palette(Func<string, string> role) => new(
        ParseColor(role("background")),
        ParseColor(role("surface")),
        ParseColor(role("surfaceVariant")),
        ParseColor(role("text")),
        ParseColor(role("textMuted")),
        ParseColor(role("outline")),
        ParseColor(role("primary")),
        ParseColor(role("onPrimary")),
        ParseColor(role("accent")),
        ParseColor(role("onAccent")),
        ParseColor(role("hero")),
        ParseColor(role("onHero")),
        ParseColor(role("heroAccent")),
        ParseColor(role("danger")));

    private static ChipColors Chip(JsonElement pair) =>
        new(ParseColor(pair.GetProperty("container").GetString()!), ParseColor(pair.GetProperty("content").GetString()!));
}

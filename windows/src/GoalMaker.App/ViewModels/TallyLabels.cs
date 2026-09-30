using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// How Tally time reads wherever it shows (docs/tally.md): a category's name, emoji and area palette
/// brush, from the shipped defaults or the owner's own categories, and minutes as hours and minutes.
/// A category that is gone still has a name, so time sorted into it is never lost from view.
/// </summary>
public sealed class TallyLabels
{
    private readonly Dictionary<string, TallyCategory> categories;
    private readonly IStrings strings;
    private readonly Func<string, Brush?> areaBrush;

    public TallyLabels(TallyDefaults defaults, IReadOnlyList<TallyCategory> own, IStrings strings, Func<string, Brush?> areaBrush)
    {
        this.strings = strings;
        this.areaBrush = areaBrush;
        categories = new Dictionary<string, TallyCategory>(StringComparer.Ordinal);
        foreach (var category in defaults.Categories.Concat(own))
        {
            categories[category.Id] = category;
        }
    }

    public string Name(string category) =>
        categories.TryGetValue(category, out var found) ? found.Name : strings.Get("Tally.GoneCategory");

    public string Emoji(string category) =>
        categories.TryGetValue(category, out var found) ? found.Emoji ?? string.Empty : string.Empty;

    /// <summary>The category's palette brush; the Other category's for one that is gone.</summary>
    public Brush? Brush(string category) =>
        areaBrush(categories.TryGetValue(category, out var found) ? found.Color
            : categories.TryGetValue(TallyRules.Other, out var other) ? other.Color : "slate");

    /// <summary>"45 min", "2 h" or "3 h 5 min".</summary>
    public string Duration(int minutes)
    {
        var hours = minutes / 60;
        var rest = minutes % 60;
        return hours == 0 ? strings.Get("Tally.Minutes", rest)
            : rest == 0 ? strings.Get("Tally.Hours", hours)
            : strings.Get("Tally.HoursMinutes", hours, rest);
    }
}

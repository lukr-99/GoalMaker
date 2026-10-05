using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One app, site or folder in the Tally page's To sort list (docs/tally.md): something on this PC that
/// landed in Other, with its time and a one-step pick of a category, which saves a rule for it.
/// <see cref="Match"/> and <see cref="Pattern"/> are the rule a pick makes.
/// </summary>
public sealed partial class TallySortRowViewModel(TallyViewModel page, string match, string pattern, string detail, string value, string moveName)
{
    public string Match { get; } = match;

    public string Pattern { get; } = pattern;

    /// <summary>"App", "Site in chrome.exe" or "Folder in code.exe".</summary>
    public string Detail { get; } = detail;

    public string Value { get; } = value;

    /// <summary>"Move notepad.exe to a category", for a screen reader.</summary>
    public string MoveName { get; } = moveName;

    /// <summary>The categories to pick from: the owner's own first, then the shipped ones, without Other.</summary>
    public IReadOnlyList<FilterChoiceViewModel> Choices => page.MoveChoices(TallyRules.Other);

    [RelayCommand]
    private void MoveTo(string category) => page.SortInto(Match, Pattern, category);
}

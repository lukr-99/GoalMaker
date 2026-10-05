using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A site or folder under an app on the Tally page, with its time, Move to (a title or folder rule in
/// one step) and Make a rule (the rule panel, filled in).
/// </summary>
public sealed partial class TallyWindowRowViewModel(
    TallyViewModel page, string category, string app, string label, string value, string makeRuleName, string moveName)
{
    public string Label { get; } = label;

    public string Value { get; } = value;

    /// <summary>"Make a rule for YouTube", for a screen reader.</summary>
    public string MakeRuleName { get; } = makeRuleName;

    /// <summary>"Move YouTube to a category", for a screen reader.</summary>
    public string MoveName { get; } = moveName;

    /// <summary>The categories it can move to: every one but its own, the owner's first.</summary>
    public IReadOnlyList<FilterChoiceViewModel> Choices => page.MoveChoices(category);

    [RelayCommand]
    private void MakeRule() => page.MakeWindowRule(category, app, Label);

    [RelayCommand]
    private void MoveTo(string into) => page.SortInto(TallyBreakdown.WindowMatch(app), Label, into);
}

using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One app under a category on the Tally page (docs/tally.md): its executable (what a rule matches),
/// its time, its sites or folders, Move to, which sorts it into another category in one step, and Make
/// a rule, which opens the rule panel for it.
/// </summary>
public sealed partial class TallyAppRowViewModel(
    TallyViewModel page, string category, string app, string value, string makeRuleName, IReadOnlyList<TallyWindowRowViewModel> windows, string moveName)
{
    public string App { get; } = app;

    public string Value { get; } = value;

    /// <summary>"Make a rule for chrome.exe", for a screen reader.</summary>
    public string MakeRuleName { get; } = makeRuleName;

    /// <summary>"Move chrome.exe to a category", for a screen reader.</summary>
    public string MoveName { get; } = moveName;

    public IReadOnlyList<TallyWindowRowViewModel> Windows { get; } = windows;

    public bool HasWindows => Windows.Count > 0;

    /// <summary>The categories it can move to: every one but its own, the owner's first.</summary>
    public IReadOnlyList<FilterChoiceViewModel> Choices => page.MoveChoices(category);

    [RelayCommand]
    private void MakeRule() => page.MakeAppRule(category, App);

    [RelayCommand]
    private void MoveTo(string into) => page.SortInto(TallyRules.App, App, into);
}

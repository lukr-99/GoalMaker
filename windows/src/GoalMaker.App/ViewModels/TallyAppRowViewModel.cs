using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One app under a category on the Tally page (docs/tally.md): its executable (what a rule matches),
/// its time, its sites or folders, and Make a rule, which starts a rule that sorts it elsewhere.
/// </summary>
public sealed partial class TallyAppRowViewModel(
    TallyViewModel page, string category, string app, string value, string makeRuleName, IReadOnlyList<TallyWindowRowViewModel> windows)
{
    public string App { get; } = app;

    public string Value { get; } = value;

    /// <summary>"Make a rule for chrome.exe", for a screen reader.</summary>
    public string MakeRuleName { get; } = makeRuleName;

    public IReadOnlyList<TallyWindowRowViewModel> Windows { get; } = windows;

    public bool HasWindows => Windows.Count > 0;

    [RelayCommand]
    private void MakeRule() => page.MakeAppRule(category, App);
}

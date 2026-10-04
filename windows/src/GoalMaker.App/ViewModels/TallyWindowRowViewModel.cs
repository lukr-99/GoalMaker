using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>A site or folder under an app on the Tally page, with its time and Make a rule (a title or folder rule).</summary>
public sealed partial class TallyWindowRowViewModel(TallyViewModel page, string category, string app, string label, string value, string makeRuleName)
{
    public string Label { get; } = label;

    public string Value { get; } = value;

    /// <summary>"Make a rule for YouTube", for a screen reader.</summary>
    public string MakeRuleName { get; } = makeRuleName;

    [RelayCommand]
    private void MakeRule() => page.MakeWindowRule(category, app, Label);
}

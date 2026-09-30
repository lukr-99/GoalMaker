using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One of the owner's Tally rules on the Tally page: what it matches, where, and the category it gives.</summary>
public sealed partial class TallyRuleRowViewModel(TallyViewModel page, TallyRule rule, string detail, Brush? brush)
{
    public TallyRule Rule { get; } = rule;

    public string Pattern => Rule.Pattern;

    /// <summary>"A window title, on the PC, goes to Games".</summary>
    public string Detail { get; } = detail;

    public Brush? Brush { get; } = brush;

    [RelayCommand]
    private void Edit() => page.StartEditRule(this);

    [RelayCommand]
    private void Delete() => page.DeleteRule(this);
}

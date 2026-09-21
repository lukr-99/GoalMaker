using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.App.Localization;
using GoalMaker.Core.Problems;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The problems card in Settings (docs/problems.md): everything that went wrong while nobody was
/// watching, newest first. Opening Settings reads them, which takes the mark off the item; they stay
/// until they come right by themselves.
/// </summary>
public sealed partial class ProblemsViewModel : ObservableObject
{
    private readonly ProblemLog problems;
    private readonly IStrings strings;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasProblems))]
    private IReadOnlyList<ProblemRowViewModel> rows = [];

    public ProblemsViewModel(ProblemLog problems, IStrings strings, Action<Action> runOnUi)
    {
        this.problems = problems;
        this.strings = strings;
        problems.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>Anything to show at all: with nothing wrong the card is not there.</summary>
    public bool HasProblems => Rows.Count > 0;

    /// <summary>The owner is looking at them, so the mark on the Settings item can go.</summary>
    public void Read() => problems.Read();

    private void Refresh() => Rows = [.. problems.Problems.Select(problem => new ProblemRowViewModel(problem, strings))];
}

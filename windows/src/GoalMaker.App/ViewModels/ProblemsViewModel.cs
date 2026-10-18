using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.App.Localization;
using GoalMaker.Core.Problems;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The problems card in Settings (docs/problems.md): everything that went wrong while nobody was
/// watching, newest first. Opening Settings reads them, which takes the mark off the item, and so does
/// one that arrives while Settings is open; they stay until they come right by themselves.
/// </summary>
public sealed partial class ProblemsViewModel : ObservableObject
{
    private readonly ProblemLog problems;
    private readonly IStrings strings;
    private bool isOpen;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasProblems))]
    private IReadOnlyList<ProblemRowViewModel> rows = [];

    public ProblemsViewModel(ProblemLog problems, IStrings strings, Action<Action> runOnUi)
    {
        this.problems = problems;
        this.strings = strings;
        problems.Changed += (_, _) => runOnUi(OnChanged);
        Refresh();
    }

    /// <summary>Anything to show at all: with nothing wrong the card is not there.</summary>
    public bool HasProblems => Rows.Count > 0;

    /// <summary>The owner opened Settings and is looking at them, so the mark on the item goes.</summary>
    public void Open()
    {
        isOpen = true;
        problems.Read();
    }

    /// <summary>The owner left Settings: what goes wrong from now on marks the item again.</summary>
    public void Close() => isOpen = false;

    private void OnChanged()
    {
        Refresh();
        // A problem that shows up while the owner is looking at Settings has been seen.
        if (isOpen)
        {
            problems.Read();
        }
    }

    private void Refresh() => Rows = [.. problems.Problems.Select(problem => new ProblemRowViewModel(problem, strings))];
}

using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One period on the Goals page: its heading and goals, Add a goal, and Copy last week's goals when it
/// has none yet but the period before had some (docs/goals.md).
/// </summary>
public sealed class GoalSectionViewModel
{
    public GoalSectionViewModel(
        GoalHorizon horizon,
        DateOnly start,
        string header,
        IReadOnlyList<GoalRowViewModel> rows,
        bool canCopy,
        string copyLabel,
        Action<GoalSectionViewModel> add,
        Action<GoalSectionViewModel> copy)
    {
        Horizon = horizon;
        Start = start;
        Header = header;
        Rows = rows;
        CanCopy = canCopy;
        CopyLabel = copyLabel;
        AddCommand = new RelayCommand(() => add(this));
        CopyCommand = new RelayCommand(() => copy(this));
    }

    public GoalHorizon Horizon { get; }

    public DateOnly Start { get; }

    public string Header { get; }

    public IReadOnlyList<GoalRowViewModel> Rows { get; }

    public bool CanCopy { get; }

    public string CopyLabel { get; }

    public IRelayCommand AddCommand { get; }

    public IRelayCommand CopyCommand { get; }
}

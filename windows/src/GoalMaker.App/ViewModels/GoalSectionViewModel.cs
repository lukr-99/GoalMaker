using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One period on the Goals page, a column of the ladder: its badge, title and how it stands, its goals
/// with the ones that need you first, Add a goal, and Copy last week's goals when it has none yet but
/// the period before had some (docs/goals.md). <see cref="IsDimmed"/> fades it while a ring shows another.
/// In the list view it is a group of compact rows.
/// </summary>
public sealed partial class GoalSectionViewModel : ObservableObject
{
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(LaneOpacity))]
    private bool isDimmed;

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

    /// <summary>"THIS WEEK · 14 TO 20 SEP", for next week's section.</summary>
    public string Header { get; }

    /// <summary>The letter on the column's badge: Y, M, W or D.</summary>
    public string Badge { get; init; } = string.Empty;

    /// <summary>The column's title: "2026", "September", "14 to 20 Sep", "Friday 18 September".</summary>
    public string Title { get; init; } = string.Empty;

    /// <summary>"1 of 3 hit", beside the list view's group header.</summary>
    public string HitText { get; init; } = string.Empty;

    /// <summary>What a screen reader says for the list view's Add button: "Add a goal to This week · 14 to 20 Sep".</summary>
    public string AddName { get; init; } = string.Empty;

    /// <summary>"1 of 3 hit · Day 5 of 7".</summary>
    public string Line { get; init; } = string.Empty;

    public IReadOnlyList<GoalRowViewModel> Rows { get; }

    public bool IsEmpty => Rows.Count == 0;

    /// <summary>How many of its goals are hit.</summary>
    public int Hits => Rows.Count(row => row.IsHit);

    /// <summary>Its ring on the dashboard: the mean of its goals' fractions, 0 with none.</summary>
    public double Fraction => Rows.Count == 0 ? 0 : Rows.Average(row => row.Fraction);

    public bool CanCopy { get; }

    public string CopyLabel { get; }

    public double LaneOpacity => IsDimmed ? 0.35 : 1;

    public IRelayCommand AddCommand { get; }

    public IRelayCommand CopyCommand { get; }
}

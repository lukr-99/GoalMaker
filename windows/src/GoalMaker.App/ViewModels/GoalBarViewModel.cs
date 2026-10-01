using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The bottom bar on Goals (docs/composer.md): a typed line becomes a goal with its period and target
/// (<see cref="QuickAddLines.ReadGoal"/>). A line that leaves no title opens the goal editor filled in
/// with what was read; so does the empty bar's plus, blank for this week.
/// </summary>
public sealed class GoalBarViewModel : BarViewModel
{
    private readonly GoalList goals;
    private readonly Func<DateOnly> today;
    private readonly Action<GoalLine?, Action> openForm;
    private GoalLine read;

    /// <param name="today">The planning day the line's periods are read against.</param>
    /// <param name="openForm">Opens the goal editor with what a line says (null: blank), and what to do once it saved.</param>
    public GoalBarViewModel(GoalList goals, IStrings strings, Func<DateOnly> today, Action<GoalLine?, Action> openForm, ChatViewModel? chat = null)
        : base(strings, chat)
    {
        this.goals = goals;
        this.today = today;
        this.openForm = openForm;
        read = QuickAddLines.ReadGoal(string.Empty, today());
        HasForm = true;
    }

    public override string FormName => Strings.Get("Goals.New");

    protected override string ItemPlaceholder => Strings.Get("Goals.BarPlaceholder");

    protected override string AddName => Strings.Get("Goals.BarAdd");

    /// <summary>The goal a line describes, as the editor takes it.</summary>
    public static GoalItem Item(GoalLine line) => new(string.Empty, line.Title, line.Horizon, line.PeriodStart)
    {
        Mode = line.Mode,
        Target = line.Target,
        Unit = line.Unit,
    };

    protected override void OnLineEdited() => read = QuickAddLines.ReadGoal(Line, today());

    protected override bool CanAdd() => Line.Trim().Length > 0;

    protected override bool Add()
    {
        if (read.Title.Length == 0)
        {
            OpenFormWith(Line.Trim());
            return false;
        }

        return goals.Add(new GoalDraft(read.Title, read.Horizon, read.PeriodStart, read.Mode, Target: read.Target, Unit: read.Unit)) is not null;
    }

    protected override void OpenFormWith(string text) =>
        openForm(text.Length > 0 ? QuickAddLines.ReadGoal(text, today()) : null, () => ClearIf(text));

    protected override IEnumerable<ComposerChipViewModel> BuildChips()
    {
        if (Line.Trim().Length == 0)
        {
            yield break;
        }

        yield return ComposerChipViewModel.Shown(Period(read.Horizon, read.PeriodStart), SymbolRegular.CalendarLtr24);
        yield return read.Target is { } target
            ? ComposerChipViewModel.Shown(Strings.Get("Goals.BarTarget", HabitRowViewModel.Amount(target), read.Unit ?? string.Empty), SymbolRegular.Target24)
            : ComposerChipViewModel.Shown(Strings.Get("Goals.ModeDone"), SymbolRegular.CheckboxChecked24);
    }

    // "This week", "Next month", or the period's own name further out ("February 2027").
    private string Period(GoalHorizon horizon, DateOnly start)
    {
        var current = GoalRules.PeriodStart(horizon, today());
        var next = GoalRules.PeriodEnd(horizon, current).AddDays(1);
        return start == current ? Strings.Get("Goals.This" + horizon)
            : start == next ? Strings.Get("Goals.Next" + horizon)
            : GoalsViewModel.PeriodText(horizon, start, Strings);
    }
}

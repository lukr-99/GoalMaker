using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The bottom bar on Habits (docs/composer.md): a typed line becomes a habit with its cadence and
/// measure (<see cref="QuickAddLines.ReadHabit"/>), starting today. A line that leaves no name opens
/// the habit editor filled in with what was read; so does the empty bar's plus, blank.
/// </summary>
public sealed class HabitBarViewModel : BarViewModel
{
    private readonly HabitList habits;
    private readonly Func<DateOnly> today;
    private readonly Action<HabitLine?, Action> openForm;
    private HabitLine read = QuickAddLines.ReadHabit(string.Empty);

    /// <param name="openForm">Opens the habit editor with what a line says (null: blank), and what to do once it saved.</param>
    public HabitBarViewModel(HabitList habits, IStrings strings, Func<DateOnly> today, Action<HabitLine?, Action> openForm, ChatViewModel? chat = null)
        : base(strings, chat)
    {
        this.habits = habits;
        this.today = today;
        this.openForm = openForm;
        HasForm = true;
    }

    public override string FormName => Strings.Get("Habits.New");

    protected override string ItemPlaceholder => Strings.Get("Habits.BarPlaceholder");

    protected override string AddName => Strings.Get("Habits.BarAdd");

    /// <summary>The habit a line describes, as the editor and the list take it.</summary>
    public static HabitItem Item(HabitLine line, DateOnly startsOn) => new(string.Empty, line.Name, startsOn)
    {
        Cadence = line.Cadence,
        Weekdays = line.Weekdays,
        Times = line.Times,
        Measure = line.Measure,
        Target = line.Target,
        Unit = line.Unit,
    };

    protected override void OnLineEdited() => read = QuickAddLines.ReadHabit(Line);

    protected override bool CanAdd() => Line.Trim().Length > 0;

    protected override bool Add()
    {
        if (read.Name.Length == 0)
        {
            OpenFormWith(Line.Trim());
            return false;
        }

        var habit = Item(read, today());
        return habits.Add(new HabitDraft(habit.Name, habit.StartsOn)
        {
            Cadence = habit.Cadence,
            Weekdays = habit.Weekdays,
            Times = habit.Times,
            Measure = habit.Measure,
            Target = habit.Target,
            Unit = habit.Unit,
        }) is not null;
    }

    protected override void OpenFormWith(string text) =>
        openForm(text.Length > 0 ? QuickAddLines.ReadHabit(text) : null, () => ClearIf(text));

    protected override IEnumerable<ComposerChipViewModel> BuildChips()
    {
        if (Line.Trim().Length == 0)
        {
            yield break;
        }

        yield return ComposerChipViewModel.Shown(HabitRowViewModel.Cadence(Item(read, today()), Strings), SymbolRegular.ArrowRepeatAll24);
        yield return read.Target is { } target
            ? ComposerChipViewModel.Shown(
                read.Unit is { } unit ? Strings.Get("Habits.BarTargetUnit", HabitRowViewModel.Amount(target), unit) : Strings.Get("Habits.BarTarget", HabitRowViewModel.Amount(target)),
                SymbolRegular.Ruler24)
            : ComposerChipViewModel.Shown(Strings.Get("Habits.BarCheck"), SymbolRegular.CheckmarkCircle24);
    }
}

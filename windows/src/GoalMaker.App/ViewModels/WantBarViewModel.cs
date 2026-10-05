using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The bottom bar on Wants (docs/composer.md): a typed line becomes a want with its price, wait and
/// reason (<see cref="QuickAddLines.ReadWant"/>). The reason is required, so a line without one opens
/// the want panel filled in with what was read; so does the empty bar's plus, blank. On the Needs tab
/// a line becomes a need: no cooldown, and the reason may be left out.
/// </summary>
public sealed class WantBarViewModel : BarViewModel
{
    private readonly WantList wants;
    private readonly Action<WantLine?, Action> openForm;
    private WantLine read = QuickAddLines.ReadWant(string.Empty);
    private bool forNeeds;

    /// <param name="openForm">Opens the want panel with what a line says (null: blank), and what to do once it saved.</param>
    public WantBarViewModel(WantList wants, IStrings strings, Action<WantLine?, Action> openForm, ChatViewModel? chat = null)
        : base(strings, chat)
    {
        this.wants = wants;
        this.openForm = openForm;
        HasForm = true;
    }

    /// <summary>Whether a typed line adds a need (the page's Needs tab) rather than a want.</summary>
    public bool ForNeeds
    {
        get => forNeeds;
        set
        {
            if (SetProperty(ref forNeeds, value))
            {
                OnPropertyChanged(nameof(Placeholder));
                OnPropertyChanged(nameof(SendName));
                OnPropertyChanged(nameof(FormName));
                OnPropertyChanged(nameof(ButtonName));
                RefreshPreview();
            }
        }
    }

    public override string FormName => Strings.Get(ForNeeds ? "Wants.NewNeed" : "Wants.New");

    protected override string ItemPlaceholder => Strings.Get(ForNeeds ? "Wants.NeedBarPlaceholder" : "Wants.BarPlaceholder");

    protected override string AddName => Strings.Get(ForNeeds ? "Wants.NeedBarAdd" : "Wants.BarAdd");

    protected override void OnLineEdited() => read = QuickAddLines.ReadWant(Line);

    // Anything typed can go: what is missing opens the panel.
    protected override bool CanAdd() => Line.Trim().Length > 0;

    protected override bool Add()
    {
        if (read.Title.Length == 0 || (read.Reason is null && !ForNeeds))
        {
            OpenFormWith(Line.Trim());
            return false;
        }

        return wants.Add(Draft(read)) is not null;
    }

    protected override void OpenFormWith(string text)
    {
        openForm(text.Length > 0 ? QuickAddLines.ReadWant(text) : null, () => ClearIf(text));
    }

    protected override IEnumerable<ComposerChipViewModel> BuildChips()
    {
        if (Line.Trim().Length == 0)
        {
            yield break;
        }

        var cooldowns = wants.Cooldowns();
        if (read.Price is { } price)
        {
            yield return ComposerChipViewModel.Shown(WantsViewModel.Money(price, read.Currency ?? cooldowns.Currency), SymbolRegular.Money24);
        }

        // A need waits for nothing and need not say why.
        if (ForNeeds)
        {
            if (read.Reason is { } note)
            {
                yield return ComposerChipViewModel.Shown(Strings.Get("Wants.BarWhy", note), SymbolRegular.TextQuote24);
            }

            yield break;
        }

        var days = WantRules.CooldownDays(read.Price, read.Currency ?? cooldowns.Currency, cooldowns, read.WaitDays);
        yield return ComposerChipViewModel.Shown(Strings.Get(days == 1 ? "Wants.BarWaitsDay" : "Wants.BarWaits", days), SymbolRegular.HourglassHalf24);
        yield return read.Reason is { } reason
            ? ComposerChipViewModel.Shown(Strings.Get("Wants.BarWhy", reason), SymbolRegular.TextQuote24)
            : ComposerChipViewModel.Shown(Strings.Get("Wants.BarNoWhy"), SymbolRegular.Warning24, warning: true);
    }

    private WantDraft Draft(WantLine line) => ForNeeds
        ? new(line.Title, line.Reason ?? string.Empty, null, line.Price, line.Currency ?? wants.Cooldowns().Currency, Kind: WantRules.Need)
        : new(line.Title, line.Reason ?? string.Empty, null, line.Price, line.Currency ?? wants.Cooldowns().Currency, PickedDays: line.WaitDays);
}

using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One want on the Wants page: its ring, where it stands, and when opened the reason, the last price
/// Claude found and the buttons that decide it (docs/wants.md). A need's row has no ring: its line
/// has the day it is needed by, in the danger colour once that day has passed.
/// </summary>
public sealed partial class WantRowViewModel : ObservableObject
{
    private readonly WantsViewModel page;

    [ObservableProperty]
    private bool isOpen;

    [ObservableProperty]
    private string note = string.Empty;

    public WantRowViewModel(WantsViewModel page, WantItem want, WantState state, double fraction, int daysLeft, string subtitle, string? checkedText, NeedLine? need = null)
    {
        this.page = page;
        Want = want;
        State = state;
        Fraction = fraction;
        Subtitle = subtitle;
        CheckedText = checkedText;
        RingText = state == WantState.Cooling ? daysLeft.ToString(CultureInfo.CurrentCulture) : string.Empty;
        Need = need ?? new NeedLine(subtitle, string.Empty, string.Empty, false);
    }

    public WantItem Want { get; }

    public WantState State { get; }

    public string Title => Want.Title;

    public string Reason => Want.Reason;

    public string? Link => Want.Link;

    public bool HasLink => !string.IsNullOrEmpty(Want.Link);

    public string Subtitle { get; }

    public string? CheckedText { get; }

    public bool HasChecked => CheckedText is not null;

    public string DecisionNote => Want.DecisionNote;

    public bool HasDecisionNote => IsDecided && Want.DecisionNote.Length > 0;

    public double Fraction { get; }

    /// <summary>The days left inside the ring while it cools; a check shows once it is ready or decided.</summary>
    public string RingText { get; }

    public bool ShowsCheck => State != WantState.Cooling;

    public bool IsReady => State == WantState.Ready;

    public bool IsDecided => State == WantState.Decided;

    public bool IsUndecided => !IsDecided;

    /// <summary>A need's line in three parts, so only the day it is needed by takes the danger colour.</summary>
    public NeedLine Need { get; }

    public bool IsLate => Need.Late;

    public bool HasReason => Want.Reason.Length > 0;

    [RelayCommand]
    private void Toggle() => IsOpen = !IsOpen;

    [RelayCommand]
    private void Buy() => page.Decide(this, WantRules.Bought, Note);

    [RelayCommand]
    private void Drop() => page.Decide(this, WantRules.Dropped, Note);

    [RelayCommand]
    private void Reopen() => page.Reopen(this);

    [RelayCommand]
    private void Delete() => page.Delete(this);

    [RelayCommand]
    private void Edit() => page.StartEdit(this);
}

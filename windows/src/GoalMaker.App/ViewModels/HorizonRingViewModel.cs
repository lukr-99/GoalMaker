using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One ring of the Goals dashboard (design prototype v1, option C): a horizon's goals, how much of them
/// is done and how many are hit. A click shows only that horizon; the other columns fade.
/// </summary>
public sealed partial class HorizonRingViewModel : ObservableObject
{
    private readonly IStrings strings;
    private readonly string summary;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(Status))]
    private bool isShown;

    public HorizonRingViewModel(GoalSectionViewModel section, IStrings strings, Action<GoalHorizon> filter)
    {
        this.strings = strings;
        Horizon = section.Horizon;
        Fraction = section.Fraction;
        Hits = section.Hits;
        Count = section.Rows.Count;
        Label = strings.Get("Goals.Ring" + section.Horizon);
        PercentText = Fraction.ToString("P0", CultureInfo.CurrentCulture);
        HitText = strings.Get("Goals.RingHit", Hits, Count);
        summary = strings.Get("Goals.RingName", Label, PercentText, HitText);
        FilterCommand = new RelayCommand(() => filter(Horizon));
    }

    public GoalHorizon Horizon { get; }

    public double Fraction { get; }

    public int Hits { get; }

    public int Count { get; }

    /// <summary>"Year", "Month", "Week", "Today".</summary>
    public string Label { get; }

    public string PercentText { get; }

    /// <summary>"1 of 3 hit".</summary>
    public string HitText { get; }

    /// <summary>What a screen reader says: "Week, 45% done, 1 of 3 hit".</summary>
    public string Name => summary;

    /// <summary>Whether the page shows only this horizon, for a screen reader.</summary>
    public string Status => strings.Get(IsShown ? "Goals.RingShown" : "Goals.RingAll");

    public IRelayCommand FilterCommand { get; }
}

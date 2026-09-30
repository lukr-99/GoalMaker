using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Navigation;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The sidebar's places (ADR 0014): the ones pinned to the top in the owner's order, All places below
/// them, the Pin toggle for the page on show, and Go to (Ctrl+K), which finds any page by typing.
/// Pins are this PC's own setting; the rules are <see cref="PlaceRules"/>.
/// </summary>
public sealed partial class PlacesViewModel : ObservableObject
{
    /// <summary>Pages Go to reaches that are not places, so they can't be pinned.</summary>
    public const string Settings = "settings";

    public const string Activity = "activity";

    public const string Areas = "areas";

    private static readonly string[] Extras = [Settings, Activity, Areas];

    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private List<string> pins;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsCurrentPinned), nameof(CanPinCurrent), nameof(PinLabel))]
    private string? current;

    [ObservableProperty]
    private bool isPaletteOpen;

    [ObservableProperty]
    private string query = string.Empty;

    [ObservableProperty]
    private int selectedIndex;

    [ObservableProperty]
    private bool hasNoMatches;

    public PlacesViewModel(ISettingsStore settings, IStrings strings)
    {
        this.settings = settings;
        this.strings = strings;
        pins = [.. settings.PinnedPlaces];
        Refresh();
    }

    /// <summary>The sidebar should be built again: the pins changed.</summary>
    public event EventHandler? PinsChanged;

    /// <summary>Go to picked a page; the shell opens it.</summary>
    public event EventHandler<string>? PlaceChosen;

    public ObservableCollection<PlaceEntry> Pinned { get; } = [];

    public ObservableCollection<PlaceEntry> Others { get; } = [];

    public ObservableCollection<PlaceEntry> Matches { get; } = [];

    public bool IsCurrentPinned => Current is { } place && pins.Contains(place);

    /// <summary>Whether the page on show is a place that can be pinned or unpinned right now.</summary>
    public bool CanPinCurrent => Current is { } place && PlaceRules.Places.Contains(place) && !(IsCurrentPinned && pins.Count <= 1);

    public string PinLabel => strings.Get(IsCurrentPinned ? "Places.Unpin" : "Places.Pin");

    public string Label(string place) => strings.Get(place switch
    {
        PlaceRules.Today => "Nav.Today",
        PlaceRules.Tomorrow => "Nav.Tomorrow",
        PlaceRules.Inbox => "Nav.Inbox",
        PlaceRules.Calendar => "Nav.Calendar",
        PlaceRules.Habits => "Nav.Habits",
        PlaceRules.Goals => "Nav.Goals",
        PlaceRules.Projects => "Nav.Projects",
        PlaceRules.Reviews => "Nav.Reviews",
        PlaceRules.Stats => "Nav.Stats",
        PlaceRules.Wants => "Nav.Wants",
        PlaceRules.Tally => "Nav.Tally",
        PlaceRules.Archive => "Nav.Archive",
        Activity => "Nav.Activity",
        Areas => "Nav.AreasAndTags",
        _ => "Nav.Settings",
    });

    public bool IsPinned(string place) => pins.Contains(place);

    /// <summary>Pins or unpins <paramref name="place"/>; false when the rules refused (the last pin, or not a place).</summary>
    public bool Toggle(string place)
    {
        var result = pins.Contains(place) ? PlaceRules.Unpin(pins, place) : PlaceRules.Pin(pins, place, DeviceKind.Pc);
        if (result.Refused)
        {
            return false;
        }

        settings.PinnedPlaces = result.Pins;
        pins = [.. settings.PinnedPlaces];
        Refresh();
        OnPropertyChanged(nameof(IsCurrentPinned));
        OnPropertyChanged(nameof(CanPinCurrent));
        OnPropertyChanged(nameof(PinLabel));
        PinsChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    [RelayCommand]
    private void TogglePinCurrent()
    {
        if (Current is { } place)
        {
            Toggle(place);
        }
    }

    [RelayCommand]
    private void OpenPalette()
    {
        Query = string.Empty;
        FilterMatches();
        IsPaletteOpen = true;
    }

    [RelayCommand]
    private void ClosePalette() => IsPaletteOpen = false;

    /// <summary>Moves the highlight in Go to by <paramref name="step"/>, stopping at either end.</summary>
    public void MoveSelection(int step)
    {
        if (Matches.Count > 0)
        {
            SelectedIndex = Math.Clamp(SelectedIndex + step, 0, Matches.Count - 1);
        }
    }

    /// <summary>Opens the highlighted match, or <paramref name="place"/> when one was clicked.</summary>
    [RelayCommand]
    private void Choose(string? place = null)
    {
        var chosen = place ?? (Matches.Count > 0 ? Matches[Math.Clamp(SelectedIndex, 0, Matches.Count - 1)].Id : null);
        if (chosen is null)
        {
            return;
        }

        IsPaletteOpen = false;
        PlaceChosen?.Invoke(this, chosen);
    }

    partial void OnQueryChanged(string value) => FilterMatches();

    private void FilterMatches()
    {
        Matches.Clear();
        foreach (var place in PlaceRules.Places.Concat(Extras))
        {
            if (Label(place).Contains(Query.Trim(), StringComparison.CurrentCultureIgnoreCase))
            {
                Matches.Add(Entry(place));
            }
        }

        SelectedIndex = 0;
        HasNoMatches = Matches.Count == 0;
    }

    private void Refresh()
    {
        Pinned.Clear();
        foreach (var place in pins)
        {
            Pinned.Add(Entry(place));
        }

        Others.Clear();
        foreach (var place in PlaceRules.Places.Where(place => !pins.Contains(place)))
        {
            Others.Add(Entry(place));
        }
    }

    private PlaceEntry Entry(string place) => new(place, Label(place));
}

using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One tile on the Places page (ADR 0014): the place's icon and name, its live numbers, and whether
/// it is pinned. A click opens the place, or pins and unpins it while the page is in Edit.
/// </summary>
public sealed partial class PlaceTileViewModel : ObservableObject
{
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowsPinMark), nameof(AutomationName))]
    private bool isPinned;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowsPinMark), nameof(AutomationName), nameof(IsActionable))]
    private bool isEditing;

    /// <summary>False for the last pin: it stays, so in Edit the tile offers nothing.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsActionable))]
    private bool canToggle = true;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsRing), nameof(IsNumber), nameof(IsLine), nameof(IsTally), nameof(IsLetter), nameof(AutomationName))]
    private PlaceTileLook look;

    [ObservableProperty]
    private double fraction;

    /// <summary>The ring's done count or the big number.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(AutomationName))]
    private string number = string.Empty;

    /// <summary>The words beside the number, or the tile's one line.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(AutomationName))]
    private string detail = string.Empty;

    [ObservableProperty]
    private IReadOnlyList<(double Amount, Brush? Brush)> parts = [];

    private readonly string pinnedText;
    private readonly string notPinnedText;

    public PlaceTileViewModel(string id, string title, string pinnedText, string notPinnedText, Action<PlaceTileViewModel> activate)
    {
        Id = id;
        Title = title;
        Icon = PlaceIcons.Of(id);
        this.pinnedText = pinnedText;
        this.notPinnedText = notPinnedText;
        ActivateCommand = new RelayCommand(() => activate(this));
    }

    public string Id { get; }

    public string Title { get; }

    public SymbolRegular Icon { get; }

    public IRelayCommand ActivateCommand { get; }

    public bool IsRing => Look == PlaceTileLook.Ring;

    public bool IsNumber => Look == PlaceTileLook.Number;

    public bool IsLine => Look == PlaceTileLook.Line;

    public bool IsTally => Look == PlaceTileLook.Tally;

    /// <summary>The hero tile: the Letter waits in Reviews.</summary>
    public bool IsLetter => Look == PlaceTileLook.Letter;

    /// <summary>Whether a click does anything: always outside Edit, and in Edit unless it is the last pin.</summary>
    public bool IsActionable => !IsEditing || CanToggle;

    /// <summary>The small pin in the corner outside Edit; in Edit the pin toggle shows instead.</summary>
    public bool ShowsPinMark => IsPinned && !IsEditing;

    /// <summary>What a screen reader says for the tile: its name, its numbers and, in Edit, whether it is pinned.</summary>
    public string AutomationName
    {
        get
        {
            var live = string.Join(" ", new[] { Number, Detail }.Where(text => text.Length > 0));
            var said = new List<string> { Title };
            if (live.Length > 0)
            {
                said.Add(live);
            }

            if (IsEditing || IsPinned)
            {
                said.Add(IsPinned ? pinnedText : notPinnedText);
            }

            return string.Join(", ", said);
        }
    }
}

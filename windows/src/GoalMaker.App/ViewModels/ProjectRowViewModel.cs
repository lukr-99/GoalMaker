using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One project in the list beside the board: its status, its area's colour, whether it is pinned, and
/// how much it has open and in Doing, so the load of every project reads at a glance without opening
/// it. The page keeps one row per project and updates it, so the list keeps the keyboard and its
/// scroll position when something changes (GM-122).
/// </summary>
public sealed partial class ProjectRowViewModel : ObservableObject
{
    [ObservableProperty]
    private string name = string.Empty;

    /// <summary>Active, paused or done, in the owner's words.</summary>
    [ObservableProperty]
    private string status = string.Empty;

    /// <summary>The same status as GoalMaker writes it down, which the row styles its mark and name by.</summary>
    [ObservableProperty]
    private string statusId = string.Empty;

    [ObservableProperty]
    private bool isSelected;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PinText))]
    private bool isPinned;

    /// <summary>How many items wait in Backlog.</summary>
    [ObservableProperty]
    private int backlog;

    /// <summary>How many items wait in To do.</summary>
    [ObservableProperty]
    private int todo;

    /// <summary>How many items are in Doing.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasDoing))]
    private int doing;

    /// <summary>Everything still open: Backlog, To do and Doing.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasOpen))]
    private int open;

    /// <summary>What the counts say to a screen reader and in the tooltip: "5 open, 2 in Doing".</summary>
    [ObservableProperty]
    private string loadText = string.Empty;

    /// <summary>The project's area colour, or none.</summary>
    [ObservableProperty]
    private Brush? areaBrush;

    private readonly string pinText;
    private readonly string unpinText;

    public ProjectRowViewModel(string id, string pinText, string unpinText, Action open, Action togglePin, Action edit)
    {
        Id = id;
        this.pinText = pinText;
        this.unpinText = unpinText;
        OpenCommand = new RelayCommand(open);
        TogglePinCommand = new RelayCommand(togglePin);
        EditCommand = new RelayCommand(edit);
    }

    public string Id { get; }

    public bool HasOpen => Open > 0;

    public bool HasDoing => Doing > 0;

    /// <summary>What the row's menu offers: pin it to the top, or let it go.</summary>
    public string PinText => IsPinned ? unpinText : pinText;

    public IRelayCommand OpenCommand { get; }

    public IRelayCommand TogglePinCommand { get; }

    public IRelayCommand EditCommand { get; }
}

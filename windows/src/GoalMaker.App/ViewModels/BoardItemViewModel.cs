using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One card on a project's board: its id (GM-12) once the server has given it a number, what it is, how
/// important it is, and where it can go.
/// </summary>
public sealed class BoardItemViewModel
{
    public BoardItemViewModel(
        string id,
        string title,
        string type,
        string itemType,
        string priority,
        bool dropped,
        string? day,
        bool madeByClaude,
        Action<string> move,
        Action open,
        Action remove,
        bool canArchive,
        Action archive,
        string? itemId = null,
        string? name = null,
        Action<string>? copyText = null)
    {
        Id = id;
        Title = title;
        ItemId = itemId ?? string.Empty;
        Name = name ?? title;
        CopyIdCommand = new RelayCommand(() => copyText?.Invoke(ItemId), () => HasItemId && copyText is not null);
        Type = type;
        ItemType = itemType;
        Priority = priority;
        Dropped = dropped;
        Day = day ?? string.Empty;
        MadeByClaude = madeByClaude;
        OpenCommand = new RelayCommand(open);
        RemoveCommand = new RelayCommand(remove);
        CanArchive = canArchive;
        ArchiveCommand = new RelayCommand(archive);
        MoveCommand = new RelayCommand<string>(column =>
        {
            if (column is not null)
            {
                move(column);
            }
        });
    }

    public string Id { get; }

    public string Title { get; }

    /// <summary>GM-12, or #12 in a project without a key; empty until the server has numbered it.</summary>
    public string ItemId { get; }

    public bool HasItemId => ItemId.Length > 0;

    /// <summary>The id and a space, written before the title in the same line of text; empty without an id.</summary>
    public string IdLead => HasItemId ? ItemId + " " : string.Empty;

    /// <summary>What a screen reader says for the card: its id, then its title.</summary>
    public string Name { get; }

    /// <summary>Puts the id on the clipboard, for a commit message or a chat.</summary>
    public IRelayCommand CopyIdCommand { get; }

    /// <summary>Task, idea or bug, in the owner's words.</summary>
    public string Type { get; }

    /// <summary>The same thing as GoalMaker writes it down, which the card styles itself by.</summary>
    public string ItemType { get; }

    /// <summary>Low, normal, high or urgent, in the owner's words.</summary>
    public string Priority { get; }

    /// <summary>A dropped item stays on the board in Dropped, greyed out.</summary>
    public bool Dropped { get; }

    /// <summary>The day it is planned for, if any.</summary>
    public string Day { get; }

    public bool HasDay => Day.Length > 0;

    /// <summary>Claude made it through the connector, which the card says in small print.</summary>
    public bool MadeByClaude { get; }

    public IRelayCommand OpenCommand { get; }

    public IRelayCommand RemoveCommand { get; }

    /// <summary>A done item can leave the board by hand; the menu offers it only then.</summary>
    public bool CanArchive { get; }

    public IRelayCommand ArchiveCommand { get; }

    /// <summary>Moves the item to the column named by the command parameter.</summary>
    public IRelayCommand<string> MoveCommand { get; }
}

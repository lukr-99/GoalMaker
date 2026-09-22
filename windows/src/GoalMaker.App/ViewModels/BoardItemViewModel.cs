using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One card on a project's board: what it is, how important it is, and where it can go.</summary>
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
        Action<string> move,
        Action open,
        Action remove)
    {
        Id = id;
        Title = title;
        Type = type;
        ItemType = itemType;
        Priority = priority;
        Dropped = dropped;
        Day = day ?? string.Empty;
        OpenCommand = new RelayCommand(open);
        RemoveCommand = new RelayCommand(remove);
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

    /// <summary>Task, idea or bug, in the owner's words.</summary>
    public string Type { get; }

    /// <summary>The same thing as GoalMaker writes it down, which the card styles itself by.</summary>
    public string ItemType { get; }

    /// <summary>Low, normal, high or urgent, in the owner's words.</summary>
    public string Priority { get; }

    /// <summary>A dropped item stays on the board, greyed out.</summary>
    public bool Dropped { get; }

    /// <summary>The day it is planned for, if any.</summary>
    public string Day { get; }

    public bool HasDay => Day.Length > 0;

    public IRelayCommand OpenCommand { get; }

    public IRelayCommand RemoveCommand { get; }

    /// <summary>Moves the item to the column named by the command parameter.</summary>
    public IRelayCommand<string> MoveCommand { get; }
}

using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;

namespace GoalMaker.App.ViewModels;

/// <summary>One column of a project's board with the cards in it (docs/projects.md).</summary>
public sealed class BoardColumnViewModel : ObservableObject
{
    public BoardColumnViewModel(string column, string title)
    {
        Column = column;
        Title = title;
        Items.CollectionChanged += (_, _) => OnPropertyChanged(nameof(IsEmpty));
    }

    /// <summary>The column's id: backlog, todo, doing or done.</summary>
    public string Column { get; }

    /// <summary>What the column is called, in the owner's words.</summary>
    public string Title { get; }

    public ObservableCollection<BoardItemViewModel> Items { get; } = [];

    public bool IsEmpty => Items.Count == 0;
}

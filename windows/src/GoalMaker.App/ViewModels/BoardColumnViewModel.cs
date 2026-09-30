using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One column of a project's board with the cards in it (docs/projects.md). A column can fold to a
/// narrow strip with its name and count; Done also counts the items that left the board and lists
/// them on request.
/// </summary>
public sealed partial class BoardColumnViewModel : ObservableObject
{
    private readonly Action<BoardColumnViewModel> folded;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsUnfolded))]
    private bool isFolded;

    [ObservableProperty]
    private string archivedText = string.Empty;

    [ObservableProperty]
    private bool showsArchived;

    public BoardColumnViewModel(string column, string title, string foldText, string unfoldText, bool isFolded, Action<BoardColumnViewModel> folded)
    {
        Column = column;
        Title = title;
        FoldText = foldText;
        UnfoldText = unfoldText;
        this.isFolded = isFolded;
        this.folded = folded;
        Items.CollectionChanged += (_, _) =>
        {
            OnPropertyChanged(nameof(IsEmpty));
            OnPropertyChanged(nameof(Count));
        };
        Archived.CollectionChanged += (_, _) => OnPropertyChanged(nameof(HasArchived));
    }

    /// <summary>The column's id: backlog, todo, doing or done.</summary>
    public string Column { get; }

    /// <summary>What the column is called, in the owner's words.</summary>
    public string Title { get; }

    /// <summary>What the fold button and the folded strip say to a screen reader.</summary>
    public string FoldText { get; }

    public string UnfoldText { get; }

    public ObservableCollection<BoardItemViewModel> Items { get; } = [];

    public bool IsEmpty => Items.Count == 0;

    /// <summary>How many cards are on the board in this column, which the header and the folded strip show.</summary>
    public int Count => Items.Count;

    public bool IsUnfolded => !IsFolded;

    /// <summary>The done items that left the board, most recently finished first; empty in the other columns.</summary>
    public ObservableCollection<ArchivedItemViewModel> Archived { get; } = [];

    public bool HasArchived => Archived.Count > 0;

    /// <summary>Folds the column to a strip, or opens it again; the page remembers it.</summary>
    [RelayCommand]
    private void Fold()
    {
        IsFolded = !IsFolded;
        folded(this);
    }

    /// <summary>Shows or hides the list of archived items under Done.</summary>
    [RelayCommand]
    private void ToggleArchived() => ShowsArchived = !ShowsArchived;
}

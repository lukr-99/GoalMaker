using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A done item that left its board: its title, when it was done, and the way back, which says
/// "Put back" when taking the hand archive off is enough and "Reopen" when the item is too old for Done.
/// Its id (GM-12) leads the title once the server has numbered it.
/// </summary>
public sealed record ArchivedItemViewModel(string Title, string DoneText, string ActionText, IRelayCommand PutBackCommand, string? ItemId = null)
{
    /// <summary>The id and a space, written before the title in the same line of text; empty without an id.</summary>
    public string IdLead => ItemId is { Length: > 0 } id ? id + " " : string.Empty;
}

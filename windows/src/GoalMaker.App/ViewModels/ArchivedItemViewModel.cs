using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A done item that left its board: its title, when it was done, and the way back, which says
/// "Put back" when taking the hand archive off is enough and "Reopen" when the item is too old for Done.
/// </summary>
public sealed record ArchivedItemViewModel(string Title, string DoneText, string ActionText, IRelayCommand PutBackCommand);

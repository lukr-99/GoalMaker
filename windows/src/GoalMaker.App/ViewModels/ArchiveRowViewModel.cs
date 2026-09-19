using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>A done task in the archive: its title, when it was done, and ways to open it or reopen it.</summary>
public sealed record ArchiveRowViewModel(string Title, string DoneText, IRelayCommand OpenCommand, IRelayCommand ReopenCommand);

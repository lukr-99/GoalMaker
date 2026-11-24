using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// An item a quick search in the project list found, in any project: its id and title, and where it
/// is, "Atlas · To do". A click takes the board there with the card picked; an item that is off the
/// board opens on its own page (GM-122).
/// </summary>
public sealed class ItemResultViewModel(TaskItem source, string? itemId, string place, string name, Action jump)
{
    internal TaskItem Source { get; } = source;

    public string Id => Source.Id;

    public string Title => Source.Title;

    public string ItemId { get; } = itemId ?? string.Empty;

    /// <summary>The id and a space, written before the title; empty without an id.</summary>
    public string IdLead => ItemId.Length > 0 ? ItemId + " " : string.Empty;

    /// <summary>The project and the column it is in.</summary>
    public string Place { get; } = place;

    /// <summary>What a screen reader says: the id, the title and where it is.</summary>
    public string Name { get; } = name;

    public IRelayCommand JumpCommand { get; } = new RelayCommand(jump);
}

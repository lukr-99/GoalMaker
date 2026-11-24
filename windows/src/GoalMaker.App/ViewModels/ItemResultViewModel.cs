using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// An item the search found, in any project: its id, its title with the matched words lit up, and where
/// it is, "Atlas · To do". A click takes the board there with the card picked; an item that is off the
/// board opens on its own page (GM-122).
/// </summary>
public sealed class ItemResultViewModel(TaskItem source, string? itemId, string place, string name, IReadOnlyList<TextPart> titleParts, Action jump)
{
    internal TaskItem Source { get; } = source;

    public string Id => Source.Id;

    public string Title => Source.Title;

    /// <summary>The title in pieces, the matched ones marked, for the result to light them up.</summary>
    public IReadOnlyList<TextPart> TitleParts { get; } = titleParts;

    public string ItemId { get; } = itemId ?? string.Empty;

    /// <summary>The id and a space, written before the title; empty without an id.</summary>
    public string IdLead => ItemId.Length > 0 ? ItemId + " " : string.Empty;

    /// <summary>The project and the column it is in.</summary>
    public string Place { get; } = place;

    /// <summary>What a screen reader says: the id, the title and where it is.</summary>
    public string Name { get; } = name;

    public IRelayCommand JumpCommand { get; } = new RelayCommand(jump);

    /// <summary>The pieces of <paramref name="text"/>, with the parts that match <paramref name="words"/> marked.</summary>
    public static IReadOnlyList<TextPart> Split(string text, IReadOnlyList<string> words)
    {
        var parts = new List<TextPart>();
        var at = 0;
        foreach (var (start, length) in SearchHighlight.Ranges(text, words))
        {
            if (start > at)
            {
                parts.Add(new TextPart(text[at..start], false));
            }

            parts.Add(new TextPart(text.Substring(start, length), true));
            at = start + length;
        }

        if (at < text.Length)
        {
            parts.Add(new TextPart(text[at..], false));
        }

        return parts;
    }
}

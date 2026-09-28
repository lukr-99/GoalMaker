using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One row of the Reviews page: a period to review, or a review already written, with its letter's first line.</summary>
public sealed class ReviewStartViewModel
{
    public ReviewStartViewModel(string title, string subtitle, string action, Action open, Action? delete, string? letter = null)
    {
        Letter = letter;
        Title = title;
        Subtitle = subtitle;
        Action = action;
        OpenCommand = new RelayCommand(open);
        DeleteCommand = new RelayCommand(() => delete?.Invoke());
        CanDelete = delete is not null;
    }

    public string Title { get; }

    public string Subtitle { get; }

    /// <summary>A review with only a letter has no mood, energy or answers to list, so the line isn't drawn empty.</summary>
    public bool HasSubtitle => Subtitle.Length > 0;

    /// <summary>The first line of the letter a Claude routine wrote about the period, or null (docs/letter.md).</summary>
    public string? Letter { get; }

    public bool HasLetter => Letter is not null;

    /// <summary>What the button says: start, read, or written.</summary>
    public string Action { get; }

    public bool CanDelete { get; }

    public IRelayCommand OpenCommand { get; }

    public IRelayCommand DeleteCommand { get; }
}

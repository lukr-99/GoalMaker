using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One row of the Reviews page: a period to review, or a review already written.</summary>
public sealed class ReviewStartViewModel
{
    public ReviewStartViewModel(string title, string subtitle, string action, Action open, Action? delete)
    {
        Title = title;
        Subtitle = subtitle;
        Action = action;
        OpenCommand = new RelayCommand(open);
        DeleteCommand = new RelayCommand(() => delete?.Invoke());
        CanDelete = delete is not null;
    }

    public string Title { get; }

    public string Subtitle { get; }

    /// <summary>What the button says: start, read, or written.</summary>
    public string Action { get; }

    public bool CanDelete { get; }

    public IRelayCommand OpenCommand { get; }

    public IRelayCommand DeleteCommand { get; }
}

using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Activity;

namespace GoalMaker.App.ViewModels;

/// <summary>One change in the Activity list: the sentence, when, and Undo when it is the row's latest change that stands.</summary>
public sealed class ActivityRowViewModel
{
    public ActivityRowViewModel(ActivityEntry entry, string sentence, string when, bool undoable, Func<ActivityRowViewModel, Task> undo)
    {
        Entry = entry;
        Sentence = sentence;
        When = when;
        Undoable = undoable;
        UndoCommand = new AsyncRelayCommand(() => undo(this));
    }

    public ActivityEntry Entry { get; }

    public string Sentence { get; }

    public string When { get; }

    public bool Undoable { get; }

    public IAsyncRelayCommand UndoCommand { get; }
}

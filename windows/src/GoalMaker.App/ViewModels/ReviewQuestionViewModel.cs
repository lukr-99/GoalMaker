using CommunityToolkit.Mvvm.ComponentModel;

namespace GoalMaker.App.ViewModels;

/// <summary>One prompt on the reflect step: its text and what the owner writes back.</summary>
public sealed partial class ReviewQuestionViewModel : ObservableObject
{
    private readonly Action changed;

    [ObservableProperty]
    private string answer;

    public ReviewQuestionViewModel(string promptId, string text, string answer, Action changed)
    {
        PromptId = promptId;
        Text = text;
        this.answer = answer;
        this.changed = changed;
    }

    public string PromptId { get; }

    public string Text { get; }

    partial void OnAnswerChanged(string value) => changed();
}

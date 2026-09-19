using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One emoji in the picker: clicking it puts it on the habit or goal being edited.</summary>
public sealed class EmojiChoiceViewModel
{
    public EmojiChoiceViewModel(string emoji, Action<string> pick)
    {
        Emoji = emoji;
        PickCommand = new RelayCommand(() => pick(emoji));
    }

    public string Emoji { get; }

    public IRelayCommand PickCommand { get; }
}

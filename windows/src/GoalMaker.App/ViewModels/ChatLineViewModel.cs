using GoalMaker.App.Localization;
using GoalMaker.Core.Assistant;

namespace GoalMaker.App.ViewModels;

/// <summary>One line of the quick chat's thread above the composer: the owner's or an answer.</summary>
public sealed class ChatLineViewModel
{
    public ChatLineViewModel(AssistantRole role, string text, IStrings strings)
    {
        Role = role;
        Text = text;
        Speaker = strings.Get(role == AssistantRole.User ? "Chat.You" : "Chat.Assistant");
        AutomationName = strings.Get("Chat.Line", Speaker, text);
    }

    public AssistantRole Role { get; }

    public string Text { get; }

    /// <summary>Who said it, in words.</summary>
    public string Speaker { get; }

    public bool IsOwner => Role == AssistantRole.User;

    /// <summary>The line as a screen reader reads it: who said it, then what.</summary>
    public string AutomationName { get; }

    public AssistantMessage ToMessage() => new(Role, Text);
}

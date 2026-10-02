namespace GoalMaker.App.ViewModels;

/// <summary>
/// What a danger row asks before it acts (docs/design/spec.md, Settings: Danger zone): a title, what
/// will happen, and the words on the button that goes ahead. The dialog focuses Cancel.
/// </summary>
public sealed record DangerQuestion(string Title, string Message, string Confirm);

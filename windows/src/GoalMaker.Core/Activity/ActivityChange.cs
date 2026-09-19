namespace GoalMaker.Core.Activity;

/// <summary>
/// What one activity entry did (docs/activity.md): <paramref name="Change"/> is added, deleted, restored,
/// completed, dropped, reopened, moved, renamed, archived, unarchived, checked, unchecked, handled,
/// snoozed or edited; <paramref name="Subject"/> is the row's title or name when it has one;
/// <paramref name="Day"/> is where a moved task went.
/// </summary>
public sealed record ActivityChange(string Change, string? Subject, string? Day = null);

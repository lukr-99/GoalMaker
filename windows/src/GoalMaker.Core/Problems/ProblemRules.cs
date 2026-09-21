namespace GoalMaker.Core.Problems;

/// <summary>
/// What the owner is told has gone wrong, and where (docs/problems.md,
/// contracts/vectors/problems.json). A kind holds one problem at a time: a newer one takes the place
/// of the older, because what matters is what is wrong now rather than every time it has been.
/// </summary>
public static class ProblemRules
{
    /// <summary>Changes that would not reach the server.</summary>
    public const string Sync = "sync";

    /// <summary>The weekly export into the owner's folder.</summary>
    public const string Backup = "backup";

    /// <summary>An update that could not be found, verified or installed.</summary>
    public const string Update = "update";

    /// <summary>Every kind both apps know, in no particular order.</summary>
    public static IReadOnlyList<string> Kinds { get; } = [Sync, Backup, Update];

    /// <summary>That kind went wrong: its problem goes to the front, unread, and any older one goes.</summary>
    public static IReadOnlyList<Problem> Report(IReadOnlyList<Problem> problems, string kind, DateTimeOffset at, string? detail) =>
        [new Problem(kind, at, detail), .. problems.Where(problem => problem.Kind != kind)];

    /// <summary>That kind came right by itself, so there is nothing left to tell.</summary>
    public static IReadOnlyList<Problem> Clear(IReadOnlyList<Problem> problems, string kind) =>
        [.. problems.Where(problem => problem.Kind != kind)];

    /// <summary>The owner opened the problems: they stay, the mark goes.</summary>
    public static IReadOnlyList<Problem> Read(IReadOnlyList<Problem> problems) =>
        [.. problems.Select(problem => problem with { Unread = false })];

    /// <summary>Whether anything is waiting to be read: the quiet mark on the Settings item.</summary>
    public static bool IsMarked(IReadOnlyList<Problem> problems) => problems.Any(problem => problem.Unread);
}

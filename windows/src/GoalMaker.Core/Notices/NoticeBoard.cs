using GoalMaker.Core.Problems;
using GoalMaker.Core.Updates;

namespace GoalMaker.Core.Notices;

/// <summary>
/// What needs the owner, in one place for the sidebar mark and the Settings banner (docs/problems.md).
/// The sidebar shows an update that waits and problems nobody has read; the banner shows the update
/// and every problem still wrong. Dismissing a line takes it off both until something new comes:
/// a newer version, or a problem that came right and went wrong again. Dismissals last as long as
/// the app runs, like the problems themselves.
/// </summary>
public sealed class NoticeBoard
{
    private readonly ProblemLog problems;
    private readonly UpdateService updates;
    private readonly HashSet<(string Kind, DateTimeOffset At)> dismissedProblems = [];
    private string? dismissedVersion;

    public NoticeBoard(ProblemLog problems, UpdateService updates)
    {
        this.problems = problems;
        this.updates = updates;
        problems.Changed += (_, _) => Changed?.Invoke(this, EventArgs.Empty);
        updates.WaitingChanged += (_, _) => Changed?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>Something was added, read, dismissed or came right. May be raised off the UI thread.</summary>
    public event EventHandler? Changed;

    /// <summary>The banner at the top of Settings: the update first, then problems, newest first.</summary>
    public IReadOnlyList<Notice> Current =>
        [.. Update(), .. problems.Problems.Where(problem => !dismissedProblems.Contains((problem.Kind, problem.At))).Select(problem => new Notice.ProblemWaiting(problem))];

    /// <summary>The icons on the sidebar's Settings item: the update, and problems nobody has read.</summary>
    public IReadOnlyList<Notice> Marked =>
        [.. Update(), .. problems.Problems.Where(problem => problem.Unread && !dismissedProblems.Contains((problem.Kind, problem.At))).Select(problem => new Notice.ProblemWaiting(problem))];

    /// <summary>The owner has dealt with it: it goes from the banner and the sidebar.</summary>
    public void Dismiss(Notice notice)
    {
        switch (notice)
        {
            case Notice.UpdateReady update:
                dismissedVersion = update.Version;
                break;
            case Notice.ProblemWaiting waiting:
                dismissedProblems.Add((waiting.Problem.Kind, waiting.Problem.At));
                break;
        }

        Changed?.Invoke(this, EventArgs.Empty);
    }

    private IEnumerable<Notice> Update() =>
        updates.Waiting?.Manifest.Version.ToString() is { } version && version != dismissedVersion
            ? [new Notice.UpdateReady(version)]
            : [];
}

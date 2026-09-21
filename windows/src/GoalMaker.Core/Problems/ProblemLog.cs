namespace GoalMaker.Core.Problems;

/// <summary>
/// The problems waiting for the owner in Settings (docs/problems.md). It lives for as long as the app
/// runs: a problem that has not come right by the next start will report itself again, and one that
/// has should never have interrupted anything in the first place.
/// </summary>
public sealed class ProblemLog(TimeProvider time)
{
    /// <summary>What is wrong now, newest first.</summary>
    public IReadOnlyList<Problem> Problems { get; private set; } = [];

    /// <summary>Whether anything is waiting to be read: the quiet mark on the Settings item.</summary>
    public bool IsMarked => ProblemRules.IsMarked(Problems);

    /// <summary>Something was added, read or came right.</summary>
    public event EventHandler? Changed;

    /// <summary>That kind went wrong; <paramref name="detail"/> is kept behind "what happened".</summary>
    public void Report(string kind, string? detail) =>
        Set(ProblemRules.Report(Problems, kind, time.GetUtcNow(), detail));

    /// <summary>That kind came right by itself.</summary>
    public void Clear(string kind) => Set(ProblemRules.Clear(Problems, kind));

    /// <summary>The owner has seen them.</summary>
    public void Read() => Set(ProblemRules.Read(Problems));

    private void Set(IReadOnlyList<Problem> problems)
    {
        if (Problems.SequenceEqual(problems))
        {
            return;
        }

        Problems = problems;
        Changed?.Invoke(this, EventArgs.Empty);
    }
}

using GoalMaker.App.Localization;
using GoalMaker.Core.Notices;
using GoalMaker.Core.Problems;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// What each notice says, in the sidebar's tooltip and in the Settings banner (docs/problems.md, "What
/// the owner sees"), and where its button takes the owner.
/// </summary>
public static class NoticeCopy
{
    /// <summary>The Settings section a notice's button jumps to, or null when the button installs the update.</summary>
    public static string? Section(Notice notice) => notice switch
    {
        Notice.ProblemWaiting { Problem.Kind: ProblemRules.Backup } => "data",
        Notice.ProblemWaiting { Problem.Kind: ProblemRules.Update } => "updates",
        Notice.ProblemWaiting => "problems",
        _ => null,
    };

    /// <summary>The banner line's heading.</summary>
    public static string Title(Notice notice, IStrings strings) => notice switch
    {
        Notice.UpdateReady update => strings.Get("Notice.UpdateReady", update.Version),
        Notice.ProblemWaiting waiting => strings.Get($"Notice.{Name(waiting.Problem.Kind)}"),
        _ => string.Empty,
    };

    /// <summary>The banner line's advice: what it means and what to do.</summary>
    public static string Advice(Notice notice, IStrings strings) => notice switch
    {
        Notice.UpdateReady => strings.Get("Notice.UpdateReadyAdvice"),
        Notice.ProblemWaiting waiting => strings.Get($"Notice.{Name(waiting.Problem.Kind)}Advice"),
        _ => string.Empty,
    };

    /// <summary>The banner line's button.</summary>
    public static string Action(Notice notice, IStrings strings) => notice switch
    {
        Notice.UpdateReady => strings.Get("Notice.Install"),
        Notice.ProblemWaiting waiting => strings.Get($"Notice.{Name(waiting.Problem.Kind)}Action"),
        _ => string.Empty,
    };

    /// <summary>The sidebar mark's tooltip and accessible name; empty with nothing marked.</summary>
    public static string Mark(IReadOnlyList<Notice> marked, IStrings strings) => marked switch
    {
        [] => string.Empty,
        [Notice.UpdateReady update] => strings.Get("Nav.Mark.UpdateReady", update.Version),
        [Notice.ProblemWaiting waiting] => strings.Get($"Nav.Mark.{Name(waiting.Problem.Kind)}"),
        _ => strings.Get("Nav.Mark.Several", marked.Count),
    };

    private static string Name(string kind) => kind switch
    {
        ProblemRules.Sync => "Sync",
        ProblemRules.Backup => "Backup",
        _ => "Update",
    };
}

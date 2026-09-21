using System.Globalization;
using GoalMaker.App.Localization;
using GoalMaker.Core.Problems;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One problem in Settings (docs/problems.md): what happened in plain words, what to do about it,
/// when it happened, and the technical line kept behind "what happened" for a bug report.
/// </summary>
public sealed class ProblemRowViewModel
{
    public ProblemRowViewModel(Problem problem, IStrings strings)
    {
        Title = strings.Get($"Problems.{Name(problem.Kind)}");
        Advice = strings.Get($"Problems.{Name(problem.Kind)}Advice");
        When = problem.At.ToLocalTime().ToString("f", CultureInfo.CurrentCulture);
        Detail = problem.Detail ?? string.Empty;
        IsUnread = problem.Unread;
    }

    public string Title { get; }

    public string Advice { get; }

    public string When { get; }

    public string Detail { get; }

    public bool HasDetail => Detail.Length > 0;

    /// <summary>Not read yet, so it is the one that put the mark on the Settings item.</summary>
    public bool IsUnread { get; }

    private static string Name(string kind) => kind switch
    {
        ProblemRules.Sync => "Sync",
        ProblemRules.Backup => "Backup",
        _ => "Update",
    };
}

using GoalMaker.Core.Versioning;

namespace GoalMaker.Core.Updates;

/// <summary>
/// Decides whether an available release is offered to the installed app. Development builds never
/// auto-update and pre-releases are never offered (contracts/vectors/semantic-version.json).
/// </summary>
public static class UpdatePolicy
{
    public static bool ShouldOffer(string installed, string available)
    {
        var current = SemanticVersion.Parse(installed);
        var candidate = SemanticVersion.Parse(available);
        if (current is null || candidate is null || current.IsDevelopmentBuild || candidate.IsPrerelease)
        {
            return false;
        }

        return candidate > current;
    }
}

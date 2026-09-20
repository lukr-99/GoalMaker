namespace GoalMaker.Core.Startup;

/// <summary>
/// The other app GoalMaker can hand itself to (spec, story 84). It is optional: with Startup Profiles
/// missing, <see cref="Find"/> returns null, the settings row stays hidden, and GoalMaker's own
/// "start in the tray when I sign in" keeps working on its own.
/// </summary>
public interface IStartupProfiles
{
    /// <summary>Where Startup Profiles is, or null when it is not installed.</summary>
    string? Find();

    /// <summary>
    /// Asks Startup Profiles to add GoalMaker, which opens that app's own confirmation window. False
    /// when it could not be reached; nothing is written either way.
    /// </summary>
    bool Ask(StartupProfilesRequest request);
}

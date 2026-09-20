namespace GoalMaker.Core.Startup;

/// <summary>
/// GoalMaker starting in the tray when the owner signs in to Windows (spec, story 81), which is what
/// keeps PC reminders working. It is GoalMaker's own setting, under GoalMaker's own key, and stays
/// whatever Startup Profiles does or does not do.
/// </summary>
public interface ISignInStartup
{
    /// <summary>Whether GoalMaker starts with Windows.</summary>
    bool IsOn { get; }

    /// <summary>Turns it on or off. False when the setting could not be written.</summary>
    bool Set(bool on);
}

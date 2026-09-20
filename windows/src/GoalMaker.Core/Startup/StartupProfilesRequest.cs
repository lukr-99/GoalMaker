namespace GoalMaker.Core.Startup;

/// <summary>
/// What GoalMaker tells Startup Profiles about itself when it asks to be added to a startup profile
/// (that app's integration contract; spec, story 84). It is metadata and a request, never authority:
/// Startup Profiles owns the decision and asks the owner in its own window, and
/// <see cref="SuggestedProfile"/> would only be a hint, so GoalMaker leaves it out.
/// </summary>
public sealed record StartupProfilesRequest(string AppId, string Name, string Target)
{
    /// <summary>The switches the profile would launch GoalMaker with; the tray, so it starts quietly.</summary>
    public string? Arguments { get; init; }

    /// <summary>Who made it, shown in the confirmation window.</summary>
    public string? Publisher { get; init; }

    /// <summary>True when the app can start out of the way; GoalMaker starts in the tray.</summary>
    public bool SupportsMinimized { get; init; }
}

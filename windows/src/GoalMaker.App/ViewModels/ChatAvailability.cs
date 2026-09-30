namespace GoalMaker.App.ViewModels;

/// <summary>Whether the composer's chat can be used now, and if not, why (M7).</summary>
public enum ChatAvailability
{
    Available,

    /// <summary>Nobody is signed in on this PC.</summary>
    SignedOut,

    /// <summary>The server can't be reached: sync says so, or the last message couldn't go.</summary>
    Offline,

    /// <summary>The server said it has no model key, so chat isn't set up there.</summary>
    NotSetUp,

    /// <summary>A development build that keeps everything on this PC has no server to chat with.</summary>
    LocalOnly,
}

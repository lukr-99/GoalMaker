namespace GoalMaker.Core.Planning;

/// <summary>
/// What Tally's tracker asks of the PC (docs/tally.md): the window in front, how long since the last
/// input, and word when another window comes to the front, the session locks or unlocks, and the PC
/// goes to sleep or wakes. The real one is Win32; the tests use their own.
/// </summary>
public interface IForegroundSource
{
    /// <summary>Another window came to the front.</summary>
    event EventHandler? Switched;

    /// <summary>The session was locked (true) or unlocked (false).</summary>
    event EventHandler<bool>? LockChanged;

    /// <summary>The PC is going to sleep (true) or woke up (false).</summary>
    event EventHandler<bool>? SleepChanged;

    /// <summary>The window in front now, or null when there is none.</summary>
    ForegroundApp? Current();

    /// <summary>How long since the last key press or mouse move.</summary>
    TimeSpan SinceInput();

    /// <summary>Starts listening; the events come only between this and <see cref="Stop"/>.</summary>
    void Start();

    void Stop();
}

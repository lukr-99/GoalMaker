namespace GoalMaker.Core.Planning;

/// <summary>
/// The daily window that holds ordinary reminders back (docs/reminders.md). The window may cross
/// midnight; one whose <see cref="Start"/> equals its <see cref="End"/> is off.
/// </summary>
public readonly record struct QuietHours(TimeOnly Start, TimeOnly End)
{
    /// <summary>No quiet hours, which is what a device starts with.</summary>
    public static QuietHours Off { get; } = new(TimeOnly.MinValue, TimeOnly.MinValue);

    /// <summary>Whether the window is switched off.</summary>
    public bool IsOff => Start == End;

    /// <summary>Whether <paramref name="time"/> falls in the window. The start counts as inside, the end as outside.</summary>
    public bool Covers(TimeOnly time) => !IsOff && (Start < End ? time >= Start && time < End : time >= Start || time < End);

    /// <summary>
    /// When a reminder due at <paramref name="at"/> actually arrives: its own time when the window
    /// is off, when it is <paramref name="important"/>, or when it falls outside; otherwise the end
    /// of the window.
    /// </summary>
    public DateTime Release(DateTime at, bool important)
    {
        if (important || !Covers(TimeOnly.FromDateTime(at)))
        {
            return at;
        }

        var day = DateOnly.FromDateTime(at);
        var sameDay = day.ToDateTime(End);
        return sameDay > at ? sameDay : day.AddDays(1).ToDateTime(End);
    }
}

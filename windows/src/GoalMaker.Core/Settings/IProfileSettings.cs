namespace GoalMaker.Core.Settings;

/// <summary>
/// The server's copy of the settings the connector needs to know what "today" is (the profile's time
/// zone and day start, supabase/migrations/0001). The device's settings stay the source; this keeps
/// the profile in step with them.
/// </summary>
public interface IProfileSettings
{
    /// <summary>Writes the device's IANA time zone id and planning-day start hour to the profile.</summary>
    Task UpdateAsync(string timeZone, int dayStartHour, CancellationToken cancellationToken = default);
}

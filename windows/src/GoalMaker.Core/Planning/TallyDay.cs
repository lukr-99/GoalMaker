namespace GoalMaker.Core.Planning;

/// <summary>
/// One synced tally_days row (docs/tally.md): a device's minutes on a planning day in a category and
/// project. <see cref="DeviceKind"/> is phone or pc; a phone's time never names a project.
/// </summary>
public sealed record TallyDay(string Id, DateOnly Day, string Device, string DeviceKind, string Category, string? ProjectId, int Minutes);

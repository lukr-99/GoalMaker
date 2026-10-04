namespace GoalMaker.Core.Planning;

/// <summary>
/// How often the why reminder comes (docs/life-goals.md), a device setting. <see cref="WhyFrequencies"/>
/// has each one's name in contracts/vectors/life-goals.json and its period's length.
/// </summary>
public enum WhyFrequency
{
    Off,
    Daily,
    Every3Days,
    Weekly,
}

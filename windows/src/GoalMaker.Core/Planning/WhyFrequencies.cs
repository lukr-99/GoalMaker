namespace GoalMaker.Core.Planning;

/// <summary>The names and period lengths of <see cref="WhyFrequency"/>, as the vectors and the settings store write them.</summary>
public static class WhyFrequencies
{
    /// <summary>What a device starts with.</summary>
    public const WhyFrequency Default = WhyFrequency.Weekly;

    public static string Key(WhyFrequency frequency) => frequency switch
    {
        WhyFrequency.Off => "off",
        WhyFrequency.Daily => "daily",
        WhyFrequency.Every3Days => "every-3-days",
        _ => "weekly",
    };

    /// <summary>A period's length in days; 0 when off.</summary>
    public static int Days(WhyFrequency frequency) => frequency switch
    {
        WhyFrequency.Off => 0,
        WhyFrequency.Daily => 1,
        WhyFrequency.Every3Days => 3,
        _ => 7,
    };

    /// <summary>The frequency named <paramref name="key"/>, or the default for anything else.</summary>
    public static WhyFrequency Of(string? key) =>
        Enum.GetValues<WhyFrequency>().FirstOrDefault(frequency => Key(frequency) == key, Default);
}

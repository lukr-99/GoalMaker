namespace GoalMaker.App.Localization;

/// <summary>
/// User-facing text by key. All copy lives in Resources/Strings.xaml so a Czech dictionary can be
/// added later (spec: English UI, strings ready for Czech). View models ask for text through this.
/// </summary>
public interface IStrings
{
    string Get(string key, params object[] arguments);
}

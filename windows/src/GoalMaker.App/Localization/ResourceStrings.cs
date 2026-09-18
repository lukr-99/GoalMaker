using System.Globalization;
using System.Windows;

namespace GoalMaker.App.Localization;

/// <summary><see cref="IStrings"/> over the application's merged resource dictionaries.</summary>
public sealed class ResourceStrings(Application application) : IStrings
{
    public string Get(string key, params object[] arguments)
    {
        var text = application.TryFindResource(key) as string ?? key;
        return arguments.Length == 0 ? text : string.Format(CultureInfo.CurrentCulture, text, arguments);
    }
}

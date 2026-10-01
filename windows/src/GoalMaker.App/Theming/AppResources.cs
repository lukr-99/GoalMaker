using System.Windows;
using DotNetLib.Tray;

namespace GoalMaker.App.Theming;

/// <summary>
/// Every dictionary the app's resources hold, in order: the tray kit's (WPF UI's themes and controls,
/// then the kit's Window style, <see cref="TrayResources.Merge"/>), then GoalMaker's own, which win
/// where both define a key. WPF UI's have to be there before GoalMaker's load: a template that bases a
/// style on a WPF UI style looks it up while its dictionary loads, and fails later if it was missing.
/// App.xaml.cs calls this once it holds the instance lock; tests call it on a plain Application,
/// never on the App class, which would start the app.
/// </summary>
public static class AppResources
{
    /// <summary>GoalMaker's own dictionaries in Resources/, in the order they merge.</summary>
    public static IReadOnlyList<string> OwnDictionaries { get; } =
        ["Strings", "Tokens", "Converters", "ComposerTemplate", "HabitTemplates", "ListTemplate", "MiniTemplates", "NavigationMarks"];

    public static void Merge(ResourceDictionary appResources)
    {
        ArgumentNullException.ThrowIfNull(appResources);

        TrayResources.Merge(appResources);
        foreach (var name in OwnDictionaries)
        {
            appResources.MergedDictionaries.Add(new ResourceDictionary { Source = new Uri($"pack://application:,,,/GoalMaker;component/Resources/{name}.xaml") });
        }
    }
}

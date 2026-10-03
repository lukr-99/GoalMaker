using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Shell;

/// <summary>
/// What a mini window is made of (spec, story 80): the view model the main window already shows and
/// the template that draws it small. Today borrows the list template, so a task ticks exactly as it
/// does on the page; habits get the shorter row in Resources/MiniTemplates.xaml.
/// </summary>
public sealed record MiniWindowContent(MiniPage Page, object ViewModel, string TemplateKey)
{
    public static MiniWindowContent For(MiniPage page, ListViewModel today, HabitsViewModel habits) => page switch
    {
        MiniPage.Habits => new MiniWindowContent(page, habits, "MiniHabitsTemplate"),
        _ => new MiniWindowContent(page, today, "ListTemplate"),
    };

    /// <summary>The name this window's place and pin are kept under.</summary>
    public string Name => Page.ToString().ToLowerInvariant();

    /// <summary>
    /// Whether the keyboard starts on <paramref name="element"/> when the window opens (M6-05): a task's
    /// done box on Today, a habit's check-in on Habits. With none of those, it starts in the composer.
    /// </summary>
    public bool IsStart(DependencyObject element) => Page switch
    {
        MiniPage.Habits => element is ButtonBase { DataContext: HabitRowViewModel },
        _ => element is CheckBox { DataContext: TaskRowViewModel },
    };
}

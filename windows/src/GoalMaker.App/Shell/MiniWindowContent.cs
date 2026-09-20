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
}

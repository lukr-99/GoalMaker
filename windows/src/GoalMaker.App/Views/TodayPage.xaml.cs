using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Today, as far as M0 goes: who is signed in, and where the composer will live.</summary>
public partial class TodayPage
{
    public TodayPage(ShellViewModel shell)
    {
        InitializeComponent();
        DataContext = shell;
    }
}

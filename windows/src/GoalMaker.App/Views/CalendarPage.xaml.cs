using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The Calendar page. Behavior is in <see cref="CalendarViewModel"/>; the page only shows it.</summary>
public partial class CalendarPage
{
    public CalendarPage(CalendarViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
    }
}

using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Today: top priorities, scheduled, more, and overdue folded (docs/lists.md).</summary>
public sealed class TodayPage(ListViewModel viewModel) : ListPage(viewModel);

using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Everything without a day or an area (docs/lists.md).</summary>
public sealed class InboxPage(ListViewModel viewModel) : ListPage(viewModel);

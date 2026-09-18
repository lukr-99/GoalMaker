using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>Tomorrow's plan; anything typed here lands on tomorrow (docs/composer.md).</summary>
public sealed class TomorrowPage(ListViewModel viewModel) : ListPage(viewModel);

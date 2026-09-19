using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>The archive of done tasks. All behavior is in <see cref="ArchiveViewModel"/>.</summary>
public partial class ArchivePage
{
    public ArchivePage(ArchiveViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}

using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using GoalMaker.App.ViewModels;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Views;

/// <summary>The Places page. Behavior is in <see cref="PlacesHubViewModel"/>; the page only shows it.</summary>
public partial class PlacesPage
{
    // A tile is never narrower than this; the page fits two to four of them on a row.
    private const double TileWidth = 230;

    private UniformGrid? grid;

    public PlacesPage(PlacesHubViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => viewModel.Refresh();
        // Edit ends when the page is left, as it does on the phone.
        Unloaded += (_, _) => viewModel.StopEditing();
        void ShowEditIcon() => EditIcon.Symbol = viewModel.IsEditing ? SymbolRegular.Checkmark20 : SymbolRegular.Pin20;
        ShowEditIcon();
        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(PlacesHubViewModel.IsEditing))
            {
                ShowEditIcon();
            }
        };
        TileList.SizeChanged += (_, _) => FitColumns();
    }

    // The panel the tiles sit in only exists once the list has made it.
    private void FitColumns()
    {
        grid ??= Find(TileList);
        if (grid is not null && TileList.ActualWidth > 0)
        {
            grid.Columns = Math.Clamp((int)(TileList.ActualWidth / TileWidth), 2, 4);
        }
    }

    private static UniformGrid? Find(DependencyObject parent)
    {
        for (var index = 0; index < VisualTreeHelper.GetChildrenCount(parent); index++)
        {
            var child = VisualTreeHelper.GetChild(parent, index);
            if ((child as UniformGrid ?? Find(child)) is { } found)
            {
                return found;
            }
        }

        return null;
    }
}

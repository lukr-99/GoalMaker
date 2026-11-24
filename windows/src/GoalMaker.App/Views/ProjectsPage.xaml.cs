using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The Projects page. Behavior is in <see cref="ProjectsViewModel"/>; the page only shows it and moves
/// the keyboard (GM-122): a click or Enter on a card opens the item, the menu key or the dots show its
/// menu, Left and Right go to the next column, Ctrl+P goes to the quick search over the project list and
/// Ctrl+F to the board's search, Esc empties the board's search or puts the list away. When the page
/// shows again after an item was opened, the keyboard goes back to that card. In a narrow window the
/// project list folds to a strip and comes out over the board.
/// </summary>
public partial class ProjectsPage
{
    // Below this width the list lies over the board instead of beside it.
    private const double NarrowBelow = 900;
    private readonly ProjectsViewModel viewModel;

    public ProjectsPage(ProjectsViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        PreviewKeyDown += OnPreviewKeyDown;
        Root.SizeChanged += (_, _) => FitWidth();
        Loaded += (_, _) =>
        {
            viewModel.CardShown -= OnCardShown;
            viewModel.CardShown += OnCardShown;
            viewModel.Refresh();
            if (viewModel.LastOpenedItem is { } id)
            {
                Dispatcher.BeginInvoke(DispatcherPriority.Loaded, () => FocusCard(id));
            }
        };
        Unloaded += (_, _) => viewModel.CardShown -= OnCardShown;
    }

    private bool IsNarrow => Equals(Root.Tag, "narrow");

    // The list goes over the board, folded away, when the window gets narrow, and back beside it, out,
    // when it gets wide again; in between, it stays as the owner left it.
    private void FitWidth()
    {
        var narrow = Root.ActualWidth < NarrowBelow;
        if (narrow == IsNarrow)
        {
            return;
        }

        Root.Tag = narrow ? "narrow" : "wide";
        viewModel.PaneOverlay = narrow;
        viewModel.IsPaneOpen = !narrow;
    }

    private void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (Keyboard.Modifiers == ModifierKeys.Control && key == Key.P)
        {
            FindProject();
            e.Handled = true;
        }
        else if (Keyboard.Modifiers == ModifierKeys.Control && key == Key.F && viewModel.ShowsProject)
        {
            BoardSearch.Focus();
            BoardSearch.SelectAll();
            e.Handled = true;
        }
        else if (key == Key.Escape && Keyboard.Modifiers == ModifierKeys.None && !PaneSearch.IsKeyboardFocusWithin)
        {
            if (viewModel.BoardQuery.Length > 0)
            {
                viewModel.BoardQuery = string.Empty;
                e.Handled = true;
            }
            else if (IsNarrow && viewModel.IsPaneOpen)
            {
                viewModel.IsPaneOpen = false;
                e.Handled = true;
            }
        }
    }

    private void OnFindProject(object sender, RoutedEventArgs e) => FindProject();

    private void FindProject()
    {
        viewModel.IsPaneOpen = true;
        Dispatcher.BeginInvoke(DispatcherPriority.Input, () =>
        {
            PaneSearch.Focus();
            PaneSearch.SelectAll();
        });
    }

    // Down from the quick search goes to the first row of the list.
    private void OnPaneSearchKey(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Down && PaneRow(0) is { } first)
        {
            first.Focus();
            e.Handled = true;
        }
    }

    // Up from the first row goes back to the quick search; the menu key shows a project's menu.
    private void OnPaneListKey(object sender, KeyEventArgs e)
    {
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (key == Key.Up && PaneRow(0) is { } first && first.IsKeyboardFocused)
        {
            PaneSearch.Focus();
            e.Handled = true;
        }
        else if ((key == Key.Apps || (key == Key.F10 && Keyboard.Modifiers == ModifierKeys.Shift))
                 && Keyboard.FocusedElement is FrameworkElement { DataContext: ProjectRowViewModel row } focused)
        {
            ShowRowMenu(focused, row, PlacementMode.Bottom);
            e.Handled = true;
        }
    }

    private void OnRowRightClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: ProjectRowViewModel row } button)
        {
            ShowRowMenu(button, row, PlacementMode.MousePoint);
            e.Handled = true;
        }
    }

    private void ShowRowMenu(UIElement target, ProjectRowViewModel row, PlacementMode placement)
    {
        var menu = (ContextMenu)FindResource("RowMenu");
        menu.DataContext = row;
        menu.PlacementTarget = target;
        menu.Placement = placement;
        menu.IsOpen = true;
    }

    // Typing on a row of the list types into the quick search, so a project is found by typing at once.
    private void OnPaneListText(object sender, TextCompositionEventArgs e)
    {
        if (e.Text.Length == 0 || !e.Text.All(char.IsLetterOrDigit))
        {
            return;
        }

        viewModel.PaneQuery += e.Text;
        PaneSearch.Focus();
        PaneSearch.CaretIndex = PaneSearch.Text.Length;
        e.Handled = true;
    }

    // The button of the n-th project or item in the list, skipping the headings.
    private ButtonBase? PaneRow(int n)
    {
        var entry = viewModel.PaneEntries.Where(candidate => candidate is ProjectRowViewModel or ItemResultViewModel).Skip(n).FirstOrDefault();
        if (entry is null)
        {
            return null;
        }

        ProjectList.UpdateLayout();
        return ProjectList.ItemContainerGenerator.ContainerFromItem(entry) is DependencyObject container ? Find<ButtonBase>(container) : null;
    }

    // A click on a card opens it, unless it was on the dots, which have their own menu.
    private void OnCardClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is ListBoxItem { DataContext: BoardItemViewModel card } && !IsInButton(e.OriginalSource as DependencyObject))
        {
            card.OpenCommand.Execute(null);
            e.Handled = true;
        }
    }

    private void OnCardRightClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is ListBoxItem { DataContext: BoardItemViewModel card } item)
        {
            ShowMenu(item, card, PlacementMode.MousePoint);
            e.Handled = true;
        }
    }

    // WPF opens a context menu on a right click only, so the dots button opens the card's menu on a
    // click, dropped under the button rather than at the mouse.
    private void OnItemMenu(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: BoardItemViewModel card } dots)
        {
            ShowMenu(dots, card, PlacementMode.Bottom);
        }
    }

    private void ShowMenu(UIElement target, BoardItemViewModel card, PlacementMode placement)
    {
        var menu = (ContextMenu)FindResource("CardMenu");
        menu.DataContext = card;
        menu.PlacementTarget = target;
        menu.Placement = placement;
        menu.IsOpen = true;
    }

    // Enter opens the picked card, the menu key shows its menu, and Left and Right go to the next column.
    private void OnCardsKey(object sender, KeyEventArgs e)
    {
        if (sender is not ListBox list || list.SelectedItem is not BoardItemViewModel card)
        {
            return;
        }

        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (key == Key.Enter)
        {
            card.OpenCommand.Execute(null);
            e.Handled = true;
        }
        else if (key == Key.Apps || (key == Key.F10 && Keyboard.Modifiers == ModifierKeys.Shift))
        {
            if (list.ItemContainerGenerator.ContainerFromItem(card) is ListBoxItem item)
            {
                ShowMenu(item, card, PlacementMode.Bottom);
            }

            e.Handled = true;
        }
        else if (key is Key.Left or Key.Right && Keyboard.Modifiers == ModifierKeys.None)
        {
            var lists = CardLists();
            var at = lists.IndexOf(list);
            var step = key == Key.Left ? -1 : 1;
            for (var i = at + step; i >= 0 && i < lists.Count; i += step)
            {
                if (lists[i].Items.Count > 0)
                {
                    Pick(lists[i], lists[i].SelectedItem as BoardItemViewModel ?? (BoardItemViewModel)lists[i].Items[0]);
                    break;
                }
            }

            e.Handled = true;
        }
    }

    private void OnCardShown(object? sender, BoardItemViewModel card) =>
        Dispatcher.BeginInvoke(DispatcherPriority.Loaded, () => FocusCard(card.Id));

    // Picks the card and gives it the keyboard, scrolled into view in its column.
    private void FocusCard(string id)
    {
        foreach (var list in CardLists())
        {
            if (list.Items.OfType<BoardItemViewModel>().FirstOrDefault(card => card.Id == id) is { } card)
            {
                Pick(list, card);
                return;
            }
        }
    }

    private static void Pick(ListBox list, BoardItemViewModel card)
    {
        list.SelectedItem = card;
        list.ScrollIntoView(card);
        list.UpdateLayout();
        (list.ItemContainerGenerator.ContainerFromItem(card) as ListBoxItem)?.Focus();
    }

    // The open columns' card lists, left to right.
    private List<ListBox> CardLists()
    {
        var lists = new List<ListBox>();
        Collect(Board, lists);
        return [.. lists.Where(list => list.Name == "Cards" && list.IsVisible)];
    }

    private static void Collect(DependencyObject node, List<ListBox> lists)
    {
        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(node); i++)
        {
            var child = VisualTreeHelper.GetChild(node, i);
            if (child is ListBox list)
            {
                lists.Add(list);
            }
            else
            {
                Collect(child, lists);
            }
        }
    }

    private static bool IsInButton(DependencyObject? node)
    {
        for (; node is not null and not ListBoxItem; node = node is Visual or System.Windows.Media.Media3D.Visual3D ? VisualTreeHelper.GetParent(node) : LogicalTreeHelper.GetParent(node))
        {
            if (node is ButtonBase)
            {
                return true;
            }
        }

        return false;
    }

    private static T? Find<T>(DependencyObject node)
        where T : DependencyObject
    {
        if (node is T found)
        {
            return found;
        }

        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(node); i++)
        {
            if (Find<T>(VisualTreeHelper.GetChild(node, i)) is { } deeper)
            {
                return deeper;
            }
        }

        return null;
    }
}

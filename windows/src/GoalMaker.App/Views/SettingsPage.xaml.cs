using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Settings. All behavior is in <see cref="SettingsViewModel"/> and, for the Claude connector,
/// problems, areas and section list, <see cref="ConnectorViewModel"/>, <see cref="ProblemsViewModel"/>,
/// <see cref="AreasViewModel"/> and <see cref="SettingsSectionsViewModel"/>; this reads the keys for
/// the quick-add shortcut, scrolls to a section, and tells the section list where the page is.
/// </summary>
public partial class SettingsPage
{
    // Below this width the section list gives its room to the page.
    private const double ListWidth = 720;

    private readonly SettingsViewModel viewModel;
    private readonly SettingsSectionsViewModel sections;
    private readonly ProblemsViewModel problems;
    private readonly Dictionary<string, FrameworkElement> cards;

    public SettingsPage(
        SettingsViewModel viewModel,
        ConnectorViewModel connector,
        ProblemsViewModel problems,
        SettingsSectionsViewModel sections,
        AreasViewModel areas)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        this.sections = sections;
        this.problems = problems;
        DataContext = viewModel;
        ConnectorCard.DataContext = connector;
        ProblemsCard.DataContext = problems;
        SectionList.DataContext = sections;
        AreasSection.DataContext = areas;
        OpenAreasButton.DataContext = sections;
        cards = new()
        {
            [SettingsSectionsViewModel.Problems] = ProblemsCard,
            [SettingsSectionsViewModel.Account] = AccountSection,
            [SettingsSectionsViewModel.Appearance] = AppearanceSection,
            [SettingsSectionsViewModel.Planning] = PlanningSection,
            [SettingsSectionsViewModel.Areas] = AreasSection,
            [SettingsSectionsViewModel.Connector] = ConnectorCard,
            [SettingsSectionsViewModel.QuickAdd] = QuickAddSection,
            [SettingsSectionsViewModel.Backup] = BackupSection,
            [SettingsSectionsViewModel.Startup] = StartupSection,
            [SettingsSectionsViewModel.MiniWindows] = MiniWindowsSection,
            [SettingsSectionsViewModel.Updates] = UpdatesSection,
            [SettingsSectionsViewModel.About] = AboutSection,
            [SettingsSectionsViewModel.Developer] = DeveloperSection,
        };
        sections.Show(problems.HasProblems);
        Listen();
        SizeChanged += (_, e) => SectionList.Visibility = e.NewSize.Width >= ListWidth ? Visibility.Visible : Visibility.Collapsed;

        // Opening Settings is reading them, so the mark on the item goes (docs/problems.md).
        Loaded += async (_, _) =>
        {
            Listen();
            problems.Read();
            await connector.RefreshAsync();
        };

        // The view models outlive the page, so the page lets go of them when it leaves.
        Unloaded += (_, _) =>
        {
            sections.JumpRequested -= OnJumpRequested;
            problems.PropertyChanged -= OnProblemsChanged;
        };
    }

    // Once only, however often the page comes back.
    private void Listen()
    {
        sections.JumpRequested -= OnJumpRequested;
        sections.JumpRequested += OnJumpRequested;
        problems.PropertyChanged -= OnProblemsChanged;
        problems.PropertyChanged += OnProblemsChanged;
    }

    // Where each shown section's card starts, in the scrolled content's coordinates.
    private Dictionary<string, double> Tops()
    {
        var tops = new Dictionary<string, double>(StringComparer.Ordinal);
        foreach (var (id, card) in cards)
        {
            if (card.Visibility == Visibility.Visible && card.IsDescendantOf(SectionsPanel))
            {
                tops[id] = card.TransformToAncestor(SectionsPanel).Transform(default).Y + SectionsPanel.Margin.Top;
            }
        }

        return tops;
    }

    private void OnJumpRequested(object? sender, string id)
    {
        if (Tops().TryGetValue(id, out var top))
        {
            Scroller.ScrollToVerticalOffset(Math.Max(0, top - 8));
        }
    }

    private void OnScrollChanged(object sender, ScrollChangedEventArgs e) =>
        sections.Follow(Tops(), Scroller.VerticalOffset, Scroller.ScrollableHeight);

    private void OnProblemsChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(ProblemsViewModel.HasProblems))
        {
            sections.Show(problems.HasProblems);
        }
    }

    // Keys pressed together in the shortcut box become the shortcut; Tab still moves on.
    private void OnQuickAddShortcutKeyDown(object sender, KeyEventArgs e)
    {
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (key == Key.Tab && Keyboard.Modifiers is ModifierKeys.None or ModifierKeys.Shift)
        {
            return;
        }

        var modifiers = Keyboard.Modifiers;
        if (Keyboard.IsKeyDown(Key.LWin) || Keyboard.IsKeyDown(Key.RWin))
        {
            modifiers |= ModifierKeys.Windows;
        }

        viewModel.RecordQuickAddHotkey(modifiers, key);
        e.Handled = true;
    }
}

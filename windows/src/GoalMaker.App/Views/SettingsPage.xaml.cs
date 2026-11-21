using System.Windows;
using System.Windows.Input;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Settings on the dotnetlib settings kit. All behavior is in <see cref="SettingsViewModel"/> and,
/// for the banner, the problems, Areas and tags and the Claude connector,
/// <see cref="SettingsNoticesViewModel"/>, <see cref="ProblemsViewModel"/>,
/// <see cref="AreasViewModel"/> and <see cref="ConnectorViewModel"/>; this hands each section its view
/// model, reads the keys for the quick-add shortcut, and lands on Updates when the mark on the
/// Settings item stood for an update (<see cref="SettingsSectionsViewModel.Landing"/>). Appearance, Planning and
/// Your data and updates are pages of their own (GM-106): three more kit pages in this view, one of which
/// shows in the main one's place.
/// </summary>
public partial class SettingsPage
{
    private readonly SettingsViewModel viewModel;

    public SettingsPage(
        SettingsViewModel viewModel,
        ConnectorViewModel connector,
        ProblemsViewModel problems,
        SettingsNoticesViewModel notices,
        SettingsSectionsViewModel sections,
        AreasViewModel areas,
        Action<Window>? prepareConfirm = null)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        ProblemsSection.DataContext = problems;
        NoticeBanner.DataContext = notices;
        ClaudeSection.DataContext = connector;
        AreasSection.DataContext = areas;
        OpenAreasRow.DataContext = sections;
        OpenAppearanceRow.Command = new RelayCommand(() => ShowPage(AppearanceKit));
        OpenPlanningRow.Command = new RelayCommand(() => ShowPage(PlanningKit));
        OpenDataRow.Command = new RelayCommand(() => ShowPage(DataKit));

        // The danger row's confirmation wears the app's body font, as its other windows do.
        SignOutAnywayRow.PrepareConfirm = prepareConfirm;

        // A banner button installs the update, or jumps to the section that helps with the problem.
        notices.ActRequested += (_, notice) =>
        {
            if (NoticeCopy.Section(notice) is { } section)
            {
                JumpTo(section);
            }
            else if (viewModel.InstallUpdateCommand.CanExecute(null))
            {
                viewModel.InstallUpdateCommand.Execute(null);
            }
        };

        // When the mark stood for an update, the page lands on Updates with the jump hint.
        Loaded += async (_, _) =>
        {
            if (SettingsSectionsViewModel.Landing(problems.HasProblems, viewModel.CanInstall) is { } section)
            {
                JumpTo(section);
            }

            await connector.RefreshAsync();
        };

        // Seeing Settings is reading the problems, so the mark on the item goes and stays off while the
        // page is on screen (docs/problems.md). A window hidden to the tray is nobody watching.
        IsVisibleChanged += (_, e) =>
        {
            if (e.NewValue is true)
            {
                problems.Open();
            }
            else
            {
                problems.Close();
            }
        };
    }

    /// <summary>The settings kit's page on screen, for its sections, jumps and snapshots.</summary>
    public DotNetLib.Tray.SettingsPage KitPage => Kits.First(kit => kit.Visibility == Visibility.Visible);

    private DotNetLib.Tray.SettingsPage[] Kits => [Kit, AppearanceKit, PlanningKit, DataKit];

    /// <summary>
    /// Scrolls to a section and plays the jump hint, on the page that holds it in full: Appearance, Planning,
    /// and Your data and Updates on the Your data and updates page. False for an unknown or hidden one.
    /// </summary>
    public bool JumpTo(string sectionId)
    {
        var kit = sectionId switch
        {
            SettingsSectionsViewModel.Appearance => AppearanceKit,
            SettingsSectionsViewModel.Planning => PlanningKit,
            SettingsSectionsViewModel.Data or SettingsSectionsViewModel.Updates => DataKit,
            _ => Kit,
        };
        ShowPage(kit);
        kit.UpdateLayout();
        return kit.JumpTo(sectionId);
    }

    /// <summary>Shows one of the kit pages in place of the others.</summary>
    private void ShowPage(DotNetLib.Tray.SettingsPage shown)
    {
        foreach (var kit in Kits)
        {
            kit.Visibility = kit == shown ? Visibility.Visible : Visibility.Collapsed;
        }
    }

    // The Back button on a page of its own goes back to the main page.
    private void OnBackToSettings(object sender, RoutedEventArgs e) => ShowPage(Kit);

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

        if (viewModel.RecordQuickAddHotkey(modifiers, key))
        {
            QuickAddRow.ShowSaved();
        }

        e.Handled = true;
    }
}

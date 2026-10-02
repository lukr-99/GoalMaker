using System.Windows;
using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Settings on the dotnetlib settings kit. All behavior is in <see cref="SettingsViewModel"/> and,
/// for the problems, Areas and tags and the Claude connector, <see cref="ProblemsViewModel"/>,
/// <see cref="AreasViewModel"/> and <see cref="ConnectorViewModel"/>; this hands each section its view
/// model, reads the keys for the quick-add shortcut, and lands on Updates when the mark on the
/// Settings item stood for an update (<see cref="SettingsSectionsViewModel.Landing"/>).
/// </summary>
public partial class SettingsPage
{
    private readonly SettingsViewModel viewModel;

    public SettingsPage(
        SettingsViewModel viewModel,
        ConnectorViewModel connector,
        ProblemsViewModel problems,
        SettingsSectionsViewModel sections,
        AreasViewModel areas,
        Action<Window>? prepareConfirm = null)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        ProblemsSection.DataContext = problems;
        ClaudeSection.DataContext = connector;
        AreasSection.DataContext = areas;
        OpenAreasRow.DataContext = sections;

        // The danger row's confirmation wears the app's body font, as its other windows do.
        SignOutAnywayRow.PrepareConfirm = prepareConfirm;

        // Opening Settings is reading them, so the mark on the item goes (docs/problems.md). When the
        // mark stood for an update, the page lands on Updates with the jump hint.
        Loaded += async (_, _) =>
        {
            if (SettingsSectionsViewModel.Landing(problems.HasProblems, viewModel.CanInstall) is { } section)
            {
                JumpTo(section);
            }

            problems.Read();
            await connector.RefreshAsync();
        };
    }

    /// <summary>The settings kit's page inside, for its sections, jumps and snapshots.</summary>
    public DotNetLib.Tray.SettingsPage KitPage => Kit;

    /// <summary>Scrolls to a section and plays the jump hint; false for an unknown or hidden one.</summary>
    public bool JumpTo(string sectionId) => Kit.JumpTo(sectionId);

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

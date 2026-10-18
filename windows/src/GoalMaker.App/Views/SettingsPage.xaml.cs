using System.Windows;
using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Settings on the dotnetlib settings kit. All behavior is in <see cref="SettingsViewModel"/> and,
/// for the banner, the problems, Areas and tags and the Claude connector,
/// <see cref="SettingsNoticesViewModel"/>, <see cref="ProblemsViewModel"/>,
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

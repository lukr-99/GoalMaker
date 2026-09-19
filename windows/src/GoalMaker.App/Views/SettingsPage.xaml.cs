using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Settings. All behavior is in <see cref="SettingsViewModel"/> and, for the Claude connector card,
/// <see cref="ConnectorViewModel"/>; this only reads the keys for the quick-add shortcut.
/// </summary>
public partial class SettingsPage
{
    private readonly SettingsViewModel viewModel;

    public SettingsPage(SettingsViewModel viewModel, ConnectorViewModel connector)
    {
        InitializeComponent();
        this.viewModel = viewModel;
        DataContext = viewModel;
        ConnectorCard.DataContext = connector;
        Loaded += async (_, _) => await connector.RefreshAsync();
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

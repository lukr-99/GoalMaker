using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using DotNetLib.Tray;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Shell;

/// <summary>
/// What a danger row in Settings asks before it acts (docs/design/spec.md, Settings: Danger zone): a
/// small dialog over the main window with what will happen, Cancel focused (Enter and Esc both
/// cancel), and the action in the danger color. The settings kit's DangerRow leaves this to the app,
/// and the kit's TrayMessageWindow focuses Yes, so GoalMaker has its own.
/// </summary>
public sealed class DangerDialog : Window
{
    private DangerDialog(DangerQuestion question, IStrings strings)
    {
        Title = question.Title;
        ShowInTaskbar = false;
        ResizeMode = ResizeMode.NoResize;
        SizeToContent = SizeToContent.WidthAndHeight;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        SetResourceReference(BackgroundProperty, "GM.SurfaceBrush");
        SetResourceReference(ForegroundProperty, "GM.TextBrush");
        SetResourceReference(FontFamilyProperty, "GM.BodyFont");

        var heading = new TextBlock { Text = question.Title, FontSize = 18, Margin = new Thickness(0, 0, 0, 8), TextWrapping = TextWrapping.Wrap };
        heading.SetResourceReference(TextBlock.FontFamilyProperty, "GM.BodyStrongFont");
        AutomationProperties.SetHeadingLevel(heading, AutomationHeadingLevel.Level1);
        var message = new TextBlock { Text = question.Message, TextWrapping = TextWrapping.Wrap, MaxWidth = 420, Margin = new Thickness(0, 0, 0, 20) };

        var cancel = new Button { Content = strings.Get("Settings.Cancel"), MinWidth = 96, IsCancel = true, IsDefault = true };
        cancel.SetResourceReference(StyleProperty, SettingsStyles.ButtonKey);
        cancel.Click += (_, _) => DialogResult = false;
        var go = new Button { Content = question.Confirm, MinWidth = 96, Margin = new Thickness(8, 0, 0, 0) };
        go.SetResourceReference(StyleProperty, SettingsStyles.DangerButtonKey);
        go.Click += (_, _) => DialogResult = true;
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        buttons.Children.Add(cancel);
        buttons.Children.Add(go);

        var layout = new StackPanel { Margin = new Thickness(24), MinWidth = 320 };
        layout.Children.Add(heading);
        layout.Children.Add(message);
        layout.Children.Add(buttons);
        Content = layout;
        Loaded += (_, _) => cancel.Focus();
    }

    /// <summary>Asks <paramref name="question"/> over the main window. True only when the owner chose the action.</summary>
    public static bool Ask(DangerQuestion question, IStrings strings)
    {
        ArgumentNullException.ThrowIfNull(question);

        var dialog = new DangerDialog(question, strings);
        if (Application.Current?.MainWindow is { IsVisible: true } owner)
        {
            dialog.Owner = owner;
        }
        else
        {
            dialog.WindowStartupLocation = WindowStartupLocation.CenterScreen;
        }

        return dialog.ShowDialog() == true;
    }
}

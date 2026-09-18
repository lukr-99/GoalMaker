using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Shell;

/// <summary>
/// The quick-add box the global shortcut opens (spec, story 11): the composer on its own, above
/// whatever app is in front, without bringing GoalMaker's window up. Enter saves the line and closes
/// the box; Escape or a click elsewhere closes it. Either way the keyboard goes back where it was.
/// </summary>
public sealed class QuickAddWindow : Window
{
    private const double BoxWidth = 560;
    private IntPtr previous;
    private bool quitting;

    public QuickAddWindow(ComposerViewModel composer, IStrings strings)
    {
        Title = strings.Get("QuickAdd.Title");
        WindowStyle = WindowStyle.None;
        ResizeMode = ResizeMode.NoResize;
        AllowsTransparency = true;
        Background = Brushes.Transparent;
        ShowInTaskbar = false;
        Topmost = true;
        Width = BoxWidth;
        SizeToContent = SizeToContent.Height;
        WindowStartupLocation = WindowStartupLocation.Manual;
        SetResourceReference(FontFamilyProperty, "GM.BodyFont");
        SetResourceReference(ForegroundProperty, "GM.TextBrush");

        var hint = new TextBlock { Text = strings.Get("QuickAdd.Hint"), FontSize = 12, Margin = new Thickness(4, 0, 0, 8) };
        hint.SetResourceReference(TextBlock.ForegroundProperty, "GM.TextMutedBrush");
        var composerHost = new ContentControl { Content = composer, Focusable = false };
        composerHost.SetResourceReference(ContentControl.ContentTemplateProperty, "ComposerTemplate");
        var frame = new Border
        {
            Padding = new Thickness(14),
            CornerRadius = new CornerRadius(14),
            BorderThickness = new Thickness(1),
            Child = new StackPanel { Children = { hint, composerHost } },
        };
        frame.SetResourceReference(Border.BackgroundProperty, "GM.BackgroundBrush");
        frame.SetResourceReference(Border.BorderBrushProperty, "GM.OutlineBrush");
        Content = frame;

        composer.Added += (_, _) => Dismiss();
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape)
            {
                e.Handled = true;
                Dismiss();
            }
        };
        Deactivated += (_, _) => Dismiss();
    }

    /// <summary>Shows the box in the upper part of the main screen with the keyboard in its text box.</summary>
    public void Summon()
    {
        var own = new WindowInteropHelper(this).Handle;
        var front = ForegroundWindow.Current();
        previous = front == own ? previous : front;
        var area = SystemParameters.WorkArea;
        Left = area.Left + ((area.Width - BoxWidth) / 2);
        Top = area.Top + (area.Height / 5);
        Show();
        Activate();
        Dispatcher.BeginInvoke(() => FirstTextBox(this)?.Focus(), System.Windows.Threading.DispatcherPriority.Input);
    }

    /// <summary>Lets the window close for good when GoalMaker quits; until then closing only hides it.</summary>
    public void CloseForGood()
    {
        quitting = true;
        Close();
    }

    protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
    {
        if (!quitting)
        {
            e.Cancel = true;
            Dismiss();
        }

        base.OnClosing(e);
    }

    private static TextBox? FirstTextBox(DependencyObject parent)
    {
        for (var index = 0; index < VisualTreeHelper.GetChildrenCount(parent); index++)
        {
            var child = VisualTreeHelper.GetChild(parent, index);
            if ((child as TextBox ?? FirstTextBox(child)) is { } found)
            {
                return found;
            }
        }

        return null;
    }

    private void Dismiss()
    {
        if (!IsVisible)
        {
            return;
        }

        Hide();
        ForegroundWindow.Restore(previous);
    }
}

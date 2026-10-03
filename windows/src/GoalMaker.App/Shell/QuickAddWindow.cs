using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Documents;
using System.Windows.Media;
using System.Windows.Media.Effects;
using GoalMaker.App.Localization;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Shell;

/// <summary>
/// The quick-add box the global shortcut opens (spec, story 11): the composer on its own, above
/// whatever app is in front, without bringing GoalMaker's window up. Enter saves the line and closes
/// the box; Escape or a click elsewhere closes it. Either way the keyboard goes back where it was. With
/// the quick chat switched on (M7), Enter sends the line instead and the box stays open for the answer.
/// Tab goes round the switch, the line and the round button, and the box grows with Windows' text size
/// (M6-05).
/// </summary>
public sealed class QuickAddWindow : Window
{
    private const double BoxWidth = 560;

    // The clear space around the box where its shadow falls.
    private const double FrameMargin = 16;
    private readonly TextScale textScale;
    private readonly TextBlock hint;
    private IntPtr previous;
    private bool quitting;

    public QuickAddWindow(ComposerViewModel composer, IStrings strings, TextScale textScale)
    {
        this.textScale = textScale;
        Title = strings.Get("QuickAdd.Title");
        WindowStyle = WindowStyle.None;
        ResizeMode = ResizeMode.NoResize;
        AllowsTransparency = true;
        Background = Brushes.Transparent;
        ShowInTaskbar = false;
        Topmost = true;
        Width = BoxWidth + (2 * FrameMargin);
        SizeToContent = SizeToContent.Height;
        WindowStartupLocation = WindowStartupLocation.Manual;
        SetResourceReference(FontFamilyProperty, "GM.BodyFont");
        SetResourceReference(ForegroundProperty, "GM.TextBrush");

        // The hint says what Enter does now, and how to flip the quick chat's switch (M7).
        hint = new TextBlock { FontSize = 12, Margin = new Thickness(6, 0, 6, 10), TextWrapping = TextWrapping.Wrap };
        hint.SetResourceReference(TextBlock.ForegroundProperty, "GM.TextMutedBrush");
        void ShowHint() => hint.Text = composer.IsChat
            ? strings.Get("QuickAdd.ChatHint")
            : composer.HasChat ? strings.Get("QuickAdd.Hint") + " " + strings.Get("QuickAdd.SwitchHint") : strings.Get("QuickAdd.Hint");
        ShowHint();
        composer.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(ComposerViewModel.IsChat))
            {
                ShowHint();
            }
        };
        var composerHost = new ContentControl { Content = composer, Focusable = false };
        composerHost.SetResourceReference(ContentControl.ContentTemplateProperty, "ComposerTemplate");
        // A floating card with a soft shadow in the window's clear margin (a sibling draws it, so the
        // text stays crisp). Its corners follow the composer's pill inside it, and its font and text
        // color are set here too, so the box looks the same wherever its content is shown.
        var frame = new Border
        {
            Margin = new Thickness(FrameMargin),
            Padding = new Thickness(12, 12, 12, 14),
            CornerRadius = new CornerRadius(28),
            BorderThickness = new Thickness(1),
            Child = new StackPanel { Children = { hint, composerHost } },
        };
        var shadow = new Border
        {
            Margin = frame.Margin,
            CornerRadius = frame.CornerRadius,
            Effect = new DropShadowEffect { BlurRadius = 24, ShadowDepth = 6, Direction = 270, Opacity = 0.28, Color = Colors.Black },
        };
        shadow.SetResourceReference(Border.BackgroundProperty, "GM.BackgroundBrush");
        frame.SetResourceReference(Border.BackgroundProperty, "GM.BackgroundBrush");
        frame.SetResourceReference(Border.BorderBrushProperty, "CardStrokeColorDefaultBrush");
        frame.SetResourceReference(TextElement.FontFamilyProperty, "GM.BodyFont");
        frame.SetResourceReference(TextElement.ForegroundProperty, "GM.TextBrush");
        var content = new Grid { Children = { shadow, frame } };
        Content = content;
        textScale.Follow(content);

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
        Width = TextScale.Grow(BoxWidth + (2 * FrameMargin), textScale.Factor, area.Width);
        Left = area.Left + ((area.Width - Width) / 2);
        Top = area.Top + (area.Height / 5);
        Show();
        Activate();
        Dispatcher.BeginInvoke(
            () =>
            {
                if (FirstTextBox(this) is { } box)
                {
                    // A reader hears the hint with the line, since the keyboard lands there and not on it.
                    System.Windows.Automation.AutomationProperties.SetHelpText(box, hint.Text);
                    box.Focus();
                }
            },
            System.Windows.Threading.DispatcherPriority.Input);
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

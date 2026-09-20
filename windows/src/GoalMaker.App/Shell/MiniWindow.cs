using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;
using GoalMaker.Core.Settings;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Shell;

/// <summary>
/// A mini window (spec, stories 80 and 82): Today or Habits on their own, small enough to keep on the
/// desktop while working, with the same view models as the main window, so ticking a task or checking
/// a habit in here is the same act. It remembers where it was, how big it was and whether it was
/// pinned on top, and closing it leaves the app running in the tray.
/// </summary>
public sealed class MiniWindow : Window
{
    private readonly MiniPage page;
    private readonly ISettingsStore settings;
    private readonly Wpf.Ui.Controls.Button pin;

    public MiniWindow(MiniWindowContent content, IStrings strings, ISettingsStore settings, Action openApp)
    {
        page = content.Page;
        this.settings = settings;
        Title = strings.Get($"Mini.{page}");
        WindowStyle = WindowStyle.None;
        ResizeMode = ResizeMode.CanResizeWithGrip;
        ShowInTaskbar = false;
        // What the list template looks at to leave out what only the main window needs.
        Tag = "mini";
        Width = DefaultSize.Width;
        Height = DefaultSize.Height;
        MinWidth = 260;
        MinHeight = 220;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        SetResourceReference(BackgroundProperty, "GM.BackgroundBrush");
        SetResourceReference(FontFamilyProperty, "GM.BodyFont");
        SetResourceReference(ForegroundProperty, "GM.TextBrush");

        pin = new Wpf.Ui.Controls.Button();
        Dress(pin, SymbolRegular.Pin24, strings.Get("Mini.Pin"));
        pin.Click += (_, _) => SetPinned(!Topmost);
        var open = new Wpf.Ui.Controls.Button();
        Dress(open, SymbolRegular.Open24, strings.Get("Mini.Open"));
        open.Click += (_, _) => openApp();
        var close = new Wpf.Ui.Controls.Button();
        Dress(close, SymbolRegular.Dismiss24, strings.Get("Mini.Close"));
        close.Click += (_, _) => Close();

        var title = new System.Windows.Controls.TextBlock
        {
            Text = Title,
            FontSize = 13,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(12, 0, 8, 0),
            TextTrimming = TextTrimming.CharacterEllipsis,
        };
        title.SetResourceReference(System.Windows.Controls.TextBlock.FontFamilyProperty, "GM.BodyStrongFont");
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        buttons.Children.Add(pin);
        buttons.Children.Add(open);
        buttons.Children.Add(close);
        var bar = new DockPanel { LastChildFill = true, Height = 34 };
        DockPanel.SetDock(buttons, Dock.Right);
        bar.Children.Add(buttons);
        bar.Children.Add(title);
        // The bar is the window's handle: the whole strip drags it, a double click does nothing else.
        bar.MouseLeftButtonDown += (_, e) =>
        {
            if (e.ButtonState == MouseButtonState.Pressed)
            {
                DragMove();
            }
        };
        var barFrame = new Border { Child = bar, BorderThickness = new Thickness(0, 0, 0, 1) };
        barFrame.SetResourceReference(Border.BackgroundProperty, "GM.SurfaceBrush");
        barFrame.SetResourceReference(Border.BorderBrushProperty, "GM.OutlineBrush");

        var host = new ContentControl
        {
            Content = content.ViewModel,
            Focusable = false,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            VerticalContentAlignment = VerticalAlignment.Stretch,
        };
        host.SetResourceReference(ContentControl.ContentTemplateProperty, content.TemplateKey);
        ScrollViewer.SetCanContentScroll(host, false);

        var layout = new DockPanel { LastChildFill = true };
        DockPanel.SetDock(barFrame, Dock.Top);
        layout.Children.Add(barFrame);
        layout.Children.Add(host);
        var frame = new Border { Child = layout, BorderThickness = new Thickness(1) };
        frame.SetResourceReference(Border.BorderBrushProperty, "GM.OutlineBrush");
        Content = frame;

        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape)
            {
                e.Handled = true;
                Close();
            }
        };
        Restore();
    }

    /// <summary>The size a mini window opens at the first time, before it has been moved or resized.</summary>
    public static Size DefaultSize { get; } = new(380, 560);

    private string Remembered => page.ToString().ToLowerInvariant();

    protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
    {
        Save();
        base.OnClosing(e);
    }

    // The title bar's buttons all look the same: an icon, no background, and a name for a reader.
    private static void Dress(Wpf.Ui.Controls.Button button, SymbolRegular symbol, string name)
    {
        button.Appearance = ControlAppearance.Transparent;
        button.Padding = new Thickness(8, 4, 8, 4);
        button.Icon = new SymbolIcon { Symbol = symbol, FontSize = 14 };
        button.ToolTip = name;
        AutomationProperties.SetName(button, name);
    }

    // Pinned: the window stays above other apps and the button shows it (spec, story 80).
    private void SetPinned(bool pinned)
    {
        Topmost = pinned;
        pin.Icon = new SymbolIcon { Symbol = pinned ? SymbolRegular.PinOff24 : SymbolRegular.Pin24, FontSize = 14 };
        pin.Appearance = pinned ? ControlAppearance.Secondary : ControlAppearance.Transparent;
        Save();
    }

    private void Restore()
    {
        if (MiniWindowMemory.Of(settings, Remembered) is not { } state)
        {
            return;
        }

        SetPinned(state.Pinned);
        if (!state.Placement.FitsWithin(
                SystemParameters.VirtualScreenLeft,
                SystemParameters.VirtualScreenTop,
                SystemParameters.VirtualScreenWidth,
                SystemParameters.VirtualScreenHeight))
        {
            return;
        }

        WindowStartupLocation = WindowStartupLocation.Manual;
        Left = state.Placement.Left;
        Top = state.Placement.Top;
        Width = state.Placement.Width;
        Height = state.Placement.Height;
    }

    private void Save()
    {
        if (!IsLoaded || WindowState != WindowState.Normal)
        {
            return;
        }

        var placement = new WindowPlacement(Left, Top, ActualWidth, ActualHeight, Maximized: false);
        MiniWindowMemory.Remember(settings, Remembered, new MiniWindowState(placement, Topmost));
    }
}

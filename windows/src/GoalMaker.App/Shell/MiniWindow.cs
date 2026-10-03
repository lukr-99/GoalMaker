using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.Core.Settings;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Shell;

/// <summary>
/// A mini window (spec, stories 80 and 82): Today or Habits on their own, small enough to keep on the
/// desktop while working, with the same view models as the main window, so ticking a task or checking
/// a habit in here is the same act. It remembers where it was, how big it was and whether it was
/// pinned on top, and closing it leaves the app running in the tray.
/// <para>
/// By keyboard (M6-05): it opens with the keyboard on Today's first task or the first habit's check-in
/// (<see cref="MiniWindowContent.IsStart"/>), Tab goes round the title bar's buttons and the content in
/// the order they read, Esc closes it once the composer has cleared its line or a picker has closed,
/// and closing hands the keyboard back to the window that had it. It grows with Windows' text size
/// (<see cref="TextScale"/>).
/// </para>
/// </summary>
public sealed class MiniWindow : Window
{
    // The smallest a mini window gets at normal text: room for Today's header with its pickers, a task
    // and the composer, so no control is ever below the edge.
    private const double SmallestWidth = 260;
    private const double SmallestHeight = 320;

    private readonly MiniWindowContent content;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TextScale textScale;
    private readonly Wpf.Ui.Controls.Button pin;
    private readonly ContentControl host;
    private IntPtr previous;
    private bool started;
    private bool handBack;

    public MiniWindow(MiniWindowContent content, IStrings strings, ISettingsStore settings, TextScale textScale, Action openApp)
    {
        this.content = content;
        this.settings = settings;
        this.strings = strings;
        this.textScale = textScale;
        Title = strings.Get($"Mini.{content.Page}");
        WindowStyle = WindowStyle.None;
        ResizeMode = ResizeMode.CanResizeWithGrip;
        ShowInTaskbar = false;
        // What the list template looks at to leave out what only the main window needs.
        Tag = "mini";
        var area = SystemParameters.WorkArea;
        Width = TextScale.Grow(DefaultSize.Width, textScale.Factor, area.Width);
        Height = TextScale.Grow(DefaultSize.Height, textScale.Factor, area.Height);
        FitSmallest();
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

        host = new ContentControl
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
        // Room for the list in a small window (M6-05): the title bar already names it, so the page's big
        // headline goes, and the page keeps a narrower margin.
        frame.Resources["GM.ListHeadlineVisibility"] = Visibility.Collapsed;
        frame.Resources["GM.PagePadding"] = new Thickness(12);
        Content = frame;
        textScale.Follow(frame);
        textScale.Changed += OnTextScaleChanged;

        // On the way up, so the composer clears its line and an open picker closes first; the Esc after
        // that closes the window.
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape)
            {
                e.Handled = true;
                Close();
            }
        };
        ContentRendered += (_, _) => StartKeyboard();
        Restore();
    }

    /// <summary>The size a mini window opens at the first time, before it has been moved or resized.</summary>
    public static Size DefaultSize { get; } = new(380, 560);

    private string Remembered => content.Name;

    /// <summary>
    /// Puts the window up with the keyboard in it, or brings it back if it is already open. The window
    /// that had the keyboard before gets it back when this one closes.
    /// </summary>
    public void Present()
    {
        var own = new WindowInteropHelper(this).Handle;
        var front = ForegroundWindow.Current();
        if (own == IntPtr.Zero || front != own)
        {
            previous = front;
        }

        Show();
        if (WindowState == WindowState.Minimized)
        {
            WindowState = WindowState.Normal;
        }

        Activate();
    }

    protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
    {
        Save();
        // Only a window that has the keyboard hands it on; one closed from elsewhere leaves it be.
        handBack = IsActive;
        base.OnClosing(e);
    }

    protected override void OnClosed(EventArgs e)
    {
        textScale.Changed -= OnTextScaleChanged;
        base.OnClosed(e);
        if (handBack)
        {
            ForegroundWindow.Restore(previous);
        }
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

    // Pinned: the window stays above other apps, and the button shows it and says what it would do now.
    private void SetPinned(bool pinned)
    {
        Topmost = pinned;
        pin.Icon = new SymbolIcon { Symbol = pinned ? SymbolRegular.PinOff24 : SymbolRegular.Pin24, FontSize = 14 };
        pin.Appearance = pinned ? ControlAppearance.Secondary : ControlAppearance.Transparent;
        var name = strings.Get(pinned ? "Mini.Unpin" : "Mini.Pin");
        pin.ToolTip = name;
        AutomationProperties.SetName(pin, name);
        Save();
    }

    // The first time the window shows, the keyboard goes where the window is for; after that WPF puts
    // it back where it was each time the window comes up again.
    private void StartKeyboard()
    {
        if (started)
        {
            return;
        }

        started = true;
        if (KeyboardStart.Find(host, content.IsStart) is { } start)
        {
            start.Focus();
        }
        else
        {
            host.MoveFocus(new TraversalRequest(FocusNavigationDirection.First));
        }
    }

    // Large text: the window gets no smaller than its smallest at normal text, grown with the text.
    private void FitSmallest()
    {
        var area = SystemParameters.WorkArea;
        MinWidth = TextScale.Grow(SmallestWidth, textScale.Factor, area.Width);
        MinHeight = TextScale.Grow(SmallestHeight, textScale.Factor, area.Height);
    }

    private void OnTextScaleChanged(object? sender, EventArgs e) => FitSmallest();

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

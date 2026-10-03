using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Media;
using Windows.UI.ViewManagement;

namespace GoalMaker.App.Theming;

/// <summary>
/// Windows' text size (Settings, Accessibility, Text size: 100 to 225%) for GoalMaker's own windows
/// (M6-05; the design spec: layouts follow the system text size). WPF draws text at the sizes the app
/// gives it and never reads that setting, so each window's content is scaled by it as a whole: text,
/// icons and spacing grow together, and the layout reflows in the room that is left, wrapping and
/// scrolling as it does in a narrow window. Menus, tooltips and drop-down lists open in their own
/// popups and keep the normal size.
/// </summary>
public sealed class TextScale : IDisposable
{
    /// <summary>The largest text size Windows offers.</summary>
    public const double Largest = 2.25;

    private readonly UISettings? settings;
    private readonly Action<Action> runOnUi;

    /// <param name="runOnUi">Runs a change on the UI thread; Windows reports one on a thread of its own.</param>
    /// <param name="readSystem">Whether to read Windows' setting; a test leaves it off and sets <see cref="Factor"/> itself.</param>
    public TextScale(Action<Action> runOnUi, bool readSystem = true)
    {
        this.runOnUi = runOnUi;
        if (!readSystem)
        {
            return;
        }

        try
        {
            settings = new UISettings();
            Factor = Clamp(settings.TextScaleFactor);
            settings.TextScaleFactorChanged += OnSystemChanged;
        }
        catch (COMException)
        {
            // No WinRT settings (a stripped-down Windows): text stays at the size the app sets.
            settings = null;
        }
    }

    /// <summary>How much larger than normal the text is: 1 to <see cref="Largest"/>.</summary>
    public double Factor { get; private set; } = 1;

    /// <summary>Raised on the UI thread when Windows' text size changes.</summary>
    public event EventHandler? Changed;

    /// <summary>A factor Windows reported, kept within the range it offers.</summary>
    public static double Clamp(double factor) => double.IsFinite(factor) ? Math.Clamp(factor, 1, Largest) : 1;

    /// <summary>Scales <paramref name="content"/> by <paramref name="factor"/> as a whole; 1 leaves it as it is.</summary>
    public static void Scale(FrameworkElement content, double factor)
    {
        ArgumentNullException.ThrowIfNull(content);
        content.LayoutTransform = factor == 1 ? Transform.Identity : new ScaleTransform(factor, factor);
    }

    /// <summary>
    /// A window's length at normal text, grown with the text as far as <paramref name="room"/> (the
    /// screen's work area) allows, and never shorter than it was.
    /// </summary>
    public static double Grow(double length, double factor, double room) => Math.Max(length, Math.Min(length * factor, room));

    /// <summary>Scales <paramref name="content"/> now, and again whenever the text size changes while it is shown.</summary>
    public void Follow(FrameworkElement content)
    {
        ArgumentNullException.ThrowIfNull(content);
        void Apply(object? sender, EventArgs e) => Scale(content, Factor);
        Scale(content, Factor);
        content.Loaded += (_, _) =>
        {
            Scale(content, Factor);
            Changed -= Apply;
            Changed += Apply;
        };
        content.Unloaded += (_, _) => Changed -= Apply;
    }

    /// <summary>Takes a new factor, as Windows reports one, and tells whatever follows it.</summary>
    public void Set(double factor)
    {
        var clamped = Clamp(factor);
        if (clamped == Factor)
        {
            return;
        }

        Factor = clamped;
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public void Dispose()
    {
        if (settings is not null)
        {
            settings.TextScaleFactorChanged -= OnSystemChanged;
        }
    }

    private void OnSystemChanged(UISettings sender, object args)
    {
        var factor = sender.TextScaleFactor;
        runOnUi(() => Set(factor));
    }
}

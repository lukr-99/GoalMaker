using System.Reflection;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Automation.Peers;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;

namespace GoalMaker.App.Tests;

/// <summary>
/// Keyboard reach without a window on the desktop (M6-05): an element hosted in a hidden window, in
/// the order WPF's own Tab moves through it, and what a screen reader calls
/// each stop. Nothing is shown or activated, so a test run never takes the keyboard from anyone.
/// </summary>
internal static class TabOrder
{
    // WPF keeps the walk Tab takes to itself; asking it is the only way to get exactly its order.
    private static readonly PropertyInfo Current = typeof(KeyboardNavigation).GetProperty("Current", BindingFlags.NonPublic | BindingFlags.Static)
        ?? throw new InvalidOperationException("WPF no longer has KeyboardNavigation.Current");

    private static readonly MethodInfo NextTab = typeof(KeyboardNavigation).GetMethod(
        "GetNextTab", BindingFlags.NonPublic | BindingFlags.Instance, [typeof(DependencyObject), typeof(DependencyObject), typeof(bool)])
        ?? throw new InvalidOperationException("WPF no longer has KeyboardNavigation.GetNextTab");

    private static readonly MethodInfo GroupParent = typeof(KeyboardNavigation).GetMethod(
        "GetGroupParent", BindingFlags.NonPublic | BindingFlags.Instance, [typeof(DependencyObject), typeof(bool)])
        ?? throw new InvalidOperationException("WPF no longer has KeyboardNavigation.GetGroupParent");

    // Which key is moving: WPF sets it for the length of a move, to Tab's property for Tab.
    private static readonly FieldInfo Moving = typeof(KeyboardNavigation).GetField("_navigationProperty", BindingFlags.NonPublic | BindingFlags.Instance)
        ?? throw new InvalidOperationException("WPF no longer has KeyboardNavigation._navigationProperty");

    /// <summary>
    /// Hosts <paramref name="element"/> in a hidden window of the given size, laid out and settled.
    /// Run it on <see cref="WpfApp"/>, whose application has the app's resources. Dispose the result to
    /// close the window.
    /// </summary>
    public static HwndSource Host(FrameworkElement element, double width, double height)
    {
        var source = new HwndSource(new HwndSourceParameters("GoalMaker test") { Width = (int)width, Height = (int)height, WindowStyle = 0 })
        {
            SizeToContent = SizeToContent.Manual,
            RootVisual = element,
        };
        element.Measure(new Size(width, height));
        element.Arrange(new Rect(0, 0, width, height));
        Settle();
        element.UpdateLayout();
        Settle();
        return source;
    }

    /// <summary>The stops Tab visits under <paramref name="root"/>, from the first, in WPF's own order.</summary>
    public static IReadOnlyList<UIElement> Stops(Visual root)
    {
        var navigation = Current.GetValue(null);
        Moving.SetValue(navigation, KeyboardNavigation.TabNavigationProperty);
        var stops = new List<UIElement>();
        // As Tab does it: the first stop in the root, then each time the next one in the group around
        // the stop the keyboard is on, until the walk comes round again or leaves the root.
        var next = (DependencyObject?)NextTab.Invoke(navigation, [null, root, true]);
        while (next is UIElement stop && stop != root && !stops.Contains(stop) && root.IsAncestorOf(stop))
        {
            stops.Add(stop);
            var group = (DependencyObject)GroupParent.Invoke(navigation, [stop, true])!;
            next = (DependencyObject?)NextTab.Invoke(navigation, [stop, group, false]);
        }

        return stops;
    }

    /// <summary>What a screen reader says a stop is called: its automation name, or its own text.</summary>
    public static string Name(UIElement element)
    {
        var peer = UIElementAutomationPeer.CreatePeerForElement(element);
        return peer?.GetName() is { Length: > 0 } name ? name : AutomationProperties.GetName(element);
    }

    /// <summary>Lets bindings, templates and layout finish what they queued.</summary>
    public static void Settle()
    {
        var frame = new DispatcherFrame();
        Dispatcher.CurrentDispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle, () => frame.Continue = false);
        Dispatcher.PushFrame(frame);
    }
}

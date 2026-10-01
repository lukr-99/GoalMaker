using System.Windows;
using System.Windows.Threading;

namespace GoalMaker.App.Controls;

/// <summary>
/// Moves the keyboard into an element when a bound value turns true, for a form that opens over a
/// page built from a template (the new task form on the lists), where there is no code-behind to do it.
/// </summary>
public static class FocusWhen
{
    public static readonly DependencyProperty OpenProperty = DependencyProperty.RegisterAttached(
        "Open", typeof(bool), typeof(FocusWhen), new PropertyMetadata(false, OnOpenChanged));

    public static bool GetOpen(DependencyObject element) => (bool)element.GetValue(OpenProperty);

    public static void SetOpen(DependencyObject element, bool value) => element.SetValue(OpenProperty, value);

    private static void OnOpenChanged(DependencyObject element, DependencyPropertyChangedEventArgs e)
    {
        if (e.NewValue is true && element is UIElement target)
        {
            target.Dispatcher.BeginInvoke(DispatcherPriority.Input, () => target.Focus());
        }
    }
}

using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;

namespace GoalMaker.App.Controls;

/// <summary>
/// Moves the keyboard into an element when a bound value turns true, for a form that opens over a
/// page built from a template (the new task form on the lists), where there is no code-behind to do it.
/// When the value turns false again the keyboard goes back where it was before (M6-05), unless it has
/// moved on to something still on show.
/// </summary>
public static class FocusWhen
{
    public static readonly DependencyProperty OpenProperty = DependencyProperty.RegisterAttached(
        "Open", typeof(bool), typeof(FocusWhen), new PropertyMetadata(false, OnOpenChanged));

    // What had the keyboard when the form opened.
    private static readonly DependencyProperty ReturnToProperty = DependencyProperty.RegisterAttached(
        "ReturnTo", typeof(UIElement), typeof(FocusWhen), new PropertyMetadata(null));

    public static bool GetOpen(DependencyObject element) => (bool)element.GetValue(OpenProperty);

    public static void SetOpen(DependencyObject element, bool value) => element.SetValue(OpenProperty, value);

    private static void OnOpenChanged(DependencyObject element, DependencyPropertyChangedEventArgs e)
    {
        if (element is not UIElement target)
        {
            return;
        }

        if (e.NewValue is true)
        {
            target.SetValue(ReturnToProperty, Keyboard.FocusedElement as UIElement);
            target.Dispatcher.BeginInvoke(DispatcherPriority.Input, () => target.Focus());
            return;
        }

        if (target.GetValue(ReturnToProperty) is not UIElement back)
        {
            return;
        }

        target.ClearValue(ReturnToProperty);
        // Once the form has gone and what it covered is back on show.
        target.Dispatcher.BeginInvoke(DispatcherPriority.Loaded, () =>
        {
            var now = Keyboard.FocusedElement as UIElement;
            if (back.IsVisible && (now is null or Window || !now.IsVisible))
            {
                back.Focus();
            }
        });
    }
}

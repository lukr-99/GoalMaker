using System.Windows;
using System.Windows.Controls;

namespace GoalMaker.App.Controls;

/// <summary>
/// Keeps a <see cref="ScrollViewer"/> at its end whenever its content grows, so the quick chat's
/// newest line is the one on screen (M7).
/// </summary>
public static class StickToEnd
{
    public static readonly DependencyProperty IsEnabledProperty = DependencyProperty.RegisterAttached(
        "IsEnabled", typeof(bool), typeof(StickToEnd), new PropertyMetadata(false, OnIsEnabledChanged));

    public static bool GetIsEnabled(DependencyObject element) => (bool)element.GetValue(IsEnabledProperty);

    public static void SetIsEnabled(DependencyObject element, bool value) => element.SetValue(IsEnabledProperty, value);

    private static void OnIsEnabledChanged(DependencyObject element, DependencyPropertyChangedEventArgs e)
    {
        if (element is not ScrollViewer viewer)
        {
            return;
        }

        viewer.ScrollChanged -= OnScrollChanged;
        if ((bool)e.NewValue)
        {
            viewer.ScrollChanged += OnScrollChanged;
        }
    }

    private static void OnScrollChanged(object sender, ScrollChangedEventArgs e)
    {
        if (e.ExtentHeightChange > 0)
        {
            ((ScrollViewer)sender).ScrollToEnd();
        }
    }
}

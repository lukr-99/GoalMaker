using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;

namespace GoalMaker.App.Controls;

/// <summary>
/// Lets a button open its own context menu on a click, under itself, the way a "more" button does. The
/// same menu also opens with a right click or the menu key, so a habit's skip is never only behind one
/// gesture (the habits redesign).
/// </summary>
public static class MenuButton
{
    public static readonly DependencyProperty OpensMenuProperty = DependencyProperty.RegisterAttached(
        "OpensMenu", typeof(bool), typeof(MenuButton), new PropertyMetadata(false, OnOpensMenuChanged));

    public static bool GetOpensMenu(DependencyObject target) => (bool)target.GetValue(OpensMenuProperty);

    public static void SetOpensMenu(DependencyObject target, bool value) => target.SetValue(OpensMenuProperty, value);

    private static void OnOpensMenuChanged(DependencyObject target, DependencyPropertyChangedEventArgs e)
    {
        if (target is not ButtonBase button)
        {
            return;
        }

        button.Click -= Open;
        if ((bool)e.NewValue)
        {
            button.Click += Open;
        }
    }

    private static void Open(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement { ContextMenu: { } menu } element)
        {
            return;
        }

        menu.DataContext = element.DataContext;
        menu.PlacementTarget = element;
        menu.Placement = PlacementMode.Bottom;
        menu.IsOpen = true;
    }
}

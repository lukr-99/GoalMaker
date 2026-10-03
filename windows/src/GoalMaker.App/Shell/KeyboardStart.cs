using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;

namespace GoalMaker.App.Shell;

/// <summary>
/// Where the keyboard goes when a window opens (M6-05): the first control on show that the window is
/// for, in the order the content reads, or else its first text box. The templates are laid out in
/// reading order, which is the order Tab takes, so the first one found is the first one Tab reaches.
/// </summary>
internal static class KeyboardStart
{
    /// <summary>The first control under <paramref name="root"/> that <paramref name="wanted"/> picks, else the first text box, else null.</summary>
    public static UIElement? Find(DependencyObject root, Func<DependencyObject, bool> wanted)
    {
        UIElement? box = null;
        foreach (var element in Reachable(root))
        {
            if (wanted(element))
            {
                return element;
            }

            box ??= element as TextBoxBase;
        }

        return box;
    }

    // Every control on show that can take the keyboard, depth first, skipping what is hidden.
    private static IEnumerable<UIElement> Reachable(DependencyObject parent)
    {
        for (var index = 0; index < VisualTreeHelper.GetChildrenCount(parent); index++)
        {
            if (VisualTreeHelper.GetChild(parent, index) is not UIElement child || child.Visibility != Visibility.Visible)
            {
                continue;
            }

            if (child.Focusable && child.IsEnabled && KeyboardNavigation.GetIsTabStop(child))
            {
                yield return child;
            }

            foreach (var inner in Reachable(child))
            {
                yield return inner;
            }
        }
    }
}

using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;

namespace GoalMaker.App.Controls;

/// <summary>
/// Whether Esc belongs to the control that has the keyboard rather than to the page (GM-122 review):
/// a text box, a picker (a ComboBox or a DatePicker, which close their list on Esc), or anything in a
/// popup or a menu. Then a page that goes back on Esc leaves it alone, and no edit is lost.
/// </summary>
public static class EscapeKey
{
    public static bool BelongsTo(DependencyObject? focused)
    {
        for (var node = focused; node is not null; node = Parent(node))
        {
            if (node is TextBoxBase or ComboBox or DatePicker or Popup or ContextMenu or MenuItem)
            {
                return true;
            }

            // The root of a popup's own window: whatever is in it is in a popup.
            if (node.GetType().Name == "PopupRoot")
            {
                return true;
            }
        }

        return false;
    }

    // Up the visual tree, and across to the logical parent where the visual one ends (a popup's content).
    private static DependencyObject? Parent(DependencyObject node) =>
        (node is Visual or System.Windows.Media.Media3D.Visual3D ? VisualTreeHelper.GetParent(node) : null)
        ?? LogicalTreeHelper.GetParent(node);
}

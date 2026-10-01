using System.Windows;
using System.Windows.Input;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Shell;

/// <summary>
/// All places in the sidebar (ADR 0014). A click on the item, Enter or Space opens the Places page and
/// leaves its list folded or unfolded as it was; only the arrow at its side, or the Right and Left
/// keys, fold and unfold the list. WPF UI's own item does both on a click.
/// </summary>
internal sealed class AllPlacesItem : NavigationViewItem
{
    private readonly Action open;

    public AllPlacesItem(Action open)
    {
        this.open = open;
        // A subclass does not pick up WPF UI's implicit style for the item on its own.
        SetResourceReference(StyleProperty, typeof(NavigationViewItem));
    }

    /// <summary>What a key does to the list: Right unfolds it, Left folds it, anything else is not ours.</summary>
    public static bool? FoldFor(Key key) => key switch
    {
        Key.Right => true,
        Key.Left => false,
        _ => null,
    };

    /// <summary>Unfolds or folds the list, when there is one, without leaving the page on show.</summary>
    public void Fold(bool unfold)
    {
        if (HasMenuItems)
        {
            SetCurrentValue(IsExpandedProperty, unfold);
        }
    }

    // The base item would fold or unfold the list too. The arrow at the side still folds it: WPF UI
    // handles a press on it before it becomes a click.
    protected override void OnClick() => open();

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (FoldFor(e.Key) is { } unfold && HasMenuItems)
        {
            Fold(unfold);
            e.Handled = true;
            return;
        }

        base.OnKeyDown(e);
    }
}

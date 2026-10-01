using System.Reflection;
using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using GoalMaker.App.Shell;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Tests;

/// <summary>
/// All places in the sidebar (ADR 0014): a click or Enter opens the Places page and leaves the list as
/// it was; only the arrow at its side, or Right and Left, fold and unfold it.
/// </summary>
public sealed class AllPlacesItemTests
{
    [Fact]
    public void AClickOpensThePlacesPageAndLeavesTheListAsItWas() => OnStaThread(() =>
    {
        var opened = 0;
        var item = Item(() => opened++, open: false);

        Click(item);
        Assert.Equal(1, opened);
        Assert.False(item.IsExpanded);

        item.IsExpanded = true;
        Click(item);
        Assert.Equal(2, opened);
        Assert.True(item.IsExpanded);
    });

    [Fact]
    public void RightAndLeftFoldTheListWithoutOpeningAnything() => OnStaThread(() =>
    {
        var opened = 0;
        var item = Item(() => opened++, open: false);

        Assert.True(Press(item, Key.Right));
        Assert.True(item.IsExpanded);
        Assert.True(Press(item, Key.Left));
        Assert.False(item.IsExpanded);
        Assert.False(Press(item, Key.Down));
        Assert.Equal(0, opened);
    });

    [Fact]
    public void WithEveryPlacePinnedThereIsNothingToFold() => OnStaThread(() =>
    {
        var item = new AllPlacesItem(() => { });

        item.Fold(true);

        Assert.False(item.IsExpanded);
        Assert.False(Press(item, Key.Right));
    });

    [Fact]
    public void OnlyTheArrowKeysFold()
    {
        Assert.True(AllPlacesItem.FoldFor(Key.Right));
        Assert.False(AllPlacesItem.FoldFor(Key.Left));
        Assert.Null(AllPlacesItem.FoldFor(Key.Enter));
        Assert.Null(AllPlacesItem.FoldFor(Key.Space));
    }

    private static AllPlacesItem Item(Action opened, bool open)
    {
        var item = new AllPlacesItem(opened);
        item.MenuItems.Add(new NavigationViewItem { Content = "Stats" });
        item.IsExpanded = open;
        return item;
    }

    // What ButtonBase does on a mouse click, Enter or Space.
    private static void Click(AllPlacesItem item) =>
        typeof(ButtonBase).GetMethod("OnClick", BindingFlags.Instance | BindingFlags.NonPublic)!.Invoke(item, null);

    // Whether the item took the key for itself.
    private static bool Press(AllPlacesItem item, Key key)
    {
        var args = new KeyEventArgs(Keyboard.PrimaryDevice, new NoSource(), 0, key) { RoutedEvent = Keyboard.KeyDownEvent };
        typeof(UIElement).GetMethod("OnKeyDown", BindingFlags.Instance | BindingFlags.NonPublic)!.Invoke(item, [args]);
        return args.Handled;
    }

    private static void OnStaThread(Action test)
    {
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                test();
            }
            catch (Exception exception)
            {
                failure = exception;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null)
        {
            throw new InvalidOperationException("The test failed on its STA thread", failure);
        }
    }

    // A key event needs a source; nothing is shown.
    private sealed class NoSource : PresentationSource
    {
        public override Visual? RootVisual { get; set; }

        public override bool IsDisposed => false;

        protected override CompositionTarget? GetCompositionTargetCore() => null;
    }
}

using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using GoalMaker.App.Controls;

namespace GoalMaker.App.Tests;

/// <summary>
/// Esc on the task page goes back only when it isn't the focused control's own (GM-122 review): a box, a
/// picker whose list Esc closes, anything in a popup or a menu.
/// </summary>
[Collection(nameof(WpfCollection))]
public sealed class EscapeKeyTests
{
    [Fact]
    public void EscBelongsToBoxesPickersPopupsAndMenus() => WpfApp.Run(() =>
    {
        var box = new TextBox();
        var combo = new ComboBox { IsEditable = true };
        var date = new DatePicker();
        var inPopup = new Button { Content = "In a popup" };
        var popup = new Popup { Child = new StackPanel { Children = { inPopup } } };
        var menuItem = new MenuItem { Header = "Delete" };
        var menu = new ContextMenu { Items = { menuItem } };
        var plain = new Button { Content = "Back" };
        var page = new StackPanel { Children = { box, combo, date, plain } };
        using var window = TabOrder.Host(page, 400, 300);

        Assert.True(EscapeKey.BelongsTo(box));
        Assert.True(EscapeKey.BelongsTo(combo));
        Assert.True(EscapeKey.BelongsTo(ProjectsPageScrollTests.Find<TextBox>(combo)));
        Assert.True(EscapeKey.BelongsTo(date));
        Assert.True(EscapeKey.BelongsTo(inPopup));
        Assert.True(EscapeKey.BelongsTo(menuItem));
        Assert.False(EscapeKey.BelongsTo(plain));
        Assert.False(EscapeKey.BelongsTo(page));
        Assert.False(EscapeKey.BelongsTo(null));
        _ = popup;
        _ = menu;
    });
}

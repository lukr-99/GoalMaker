using System.Windows.Controls;
using DotNetLib.Tray;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;

namespace GoalMaker.App.Shell;

/// <summary>
/// The tray icon's right-click menu (spec, stories 79, 80 and 11), built with the tray kit's
/// <see cref="TrayMenuBuilder"/>: open GoalMaker, add a task through the quick-add box, open a mini
/// window, or quit. Open is the bold default item, because a double click on the icon does it.
/// </summary>
public static class TrayMenu
{
    public static ContextMenu Build(IStrings strings, Action open, Action quickAdd, Action<MiniPage> mini, Action quit)
    {
        ArgumentNullException.ThrowIfNull(strings);

        return new TrayMenuBuilder()
            .Item(strings.Get("Tray.Open"), open, isDefault: true)
            .Item(strings.Get("Tray.QuickAdd"), quickAdd)
            .Separator()
            .Item(strings.Get("Tray.MiniToday"), () => mini(MiniPage.Today))
            .Item(strings.Get("Tray.MiniHabits"), () => mini(MiniPage.Habits))
            .Separator()
            .Item(strings.Get("Tray.Quit"), quit)
            .Build();
    }
}

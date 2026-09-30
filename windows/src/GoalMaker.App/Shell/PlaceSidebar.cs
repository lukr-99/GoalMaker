using System.Windows;
using System.Windows.Controls;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Navigation;
using Wpf.Ui.Controls;
using MenuItem = System.Windows.Controls.MenuItem;

namespace GoalMaker.App.Shell;

/// <summary>
/// The sidebar's items (ADR 0014): a Pinned header with the pinned places in the owner's order, then
/// All places, which folds out the rest. Every place offers Pin or Unpin on a right click.
/// </summary>
internal static class PlaceSidebar
{
    /// <summary>The tag All places carries, so the shell can tell whether it is folded out.</summary>
    public const string AllPlaces = "all-places";

    private static readonly Dictionary<string, (Type Page, SymbolRegular Icon)> Places = new(StringComparer.Ordinal)
    {
        [PlaceRules.Today] = (typeof(TodayPage), SymbolRegular.CalendarToday24),
        [PlaceRules.Tomorrow] = (typeof(TomorrowPage), SymbolRegular.CalendarArrowRight24),
        [PlaceRules.Inbox] = (typeof(InboxPage), SymbolRegular.MailInbox24),
        [PlaceRules.Calendar] = (typeof(CalendarPage), SymbolRegular.CalendarLtr24),
        [PlaceRules.Habits] = (typeof(HabitsPage), SymbolRegular.ArrowRepeatAll24),
        [PlaceRules.Goals] = (typeof(GoalsPage), SymbolRegular.Flag24),
        [PlaceRules.Projects] = (typeof(ProjectsPage), SymbolRegular.Board24),
        [PlaceRules.Reviews] = (typeof(ReviewsPage), SymbolRegular.BookOpen24),
        [PlaceRules.Stats] = (typeof(StatsPage), SymbolRegular.DataTrending24),
        [PlaceRules.Wants] = (typeof(WantsPage), SymbolRegular.ShoppingBag24),
        [PlaceRules.Tally] = (typeof(TallyPage), SymbolRegular.Timer24),
        [PlaceRules.Archive] = (typeof(ArchivePage), SymbolRegular.Archive24),
        [PlacesViewModel.Settings] = (typeof(SettingsPage), SymbolRegular.Settings24),
        [PlacesViewModel.Activity] = (typeof(ActivityPage), SymbolRegular.History24),
        [PlacesViewModel.Areas] = (typeof(AreasPage), SymbolRegular.Tag24),
    };

    public static Type PageOf(string place) => Places.TryGetValue(place, out var look) ? look.Page : typeof(TodayPage);

    /// <summary>The place a page is, or null for a page that is not one (a task, the review, Plan tomorrow).</summary>
    public static string? PlaceOf(Type page) => Places.FirstOrDefault(entry => entry.Value.Page == page).Key;

    /// <summary>Fills <paramref name="items"/> again from the pins, keeping All places open or folded as it was.</summary>
    public static void Build(System.Collections.IList items, PlacesViewModel places, bool allOpen)
    {
        items.Clear();
        // The pages' own section label: small, strong, in the accent (WPF UI's item header does not
        // draw in these themes). Hidden with the sidebar folded to icons.
        var label = new System.Windows.Controls.TextBlock
        {
            Text = ((string)Application.Current.FindResource("Places.Pinned")).ToUpper(System.Globalization.CultureInfo.CurrentCulture),
            FontSize = 11,
            Margin = new Thickness(14, 6, 0, 4),
            IsHitTestVisible = false,
        };
        label.SetResourceReference(System.Windows.Controls.TextBlock.FontFamilyProperty, "GM.BodyStrongFont");
        label.SetResourceReference(System.Windows.Controls.TextBlock.ForegroundProperty, "GM.AccentBrush");
        items.Add(label);
        foreach (var entry in places.Pinned)
        {
            items.Add(Item(entry, places));
        }

        if (places.Others.Count == 0)
        {
            return;
        }

        var all = new NavigationViewItem
        {
            Content = Application.Current.FindResource("Places.All"),
            Icon = new SymbolIcon { Symbol = SymbolRegular.Apps24 },
            IsExpanded = allOpen,
            Tag = AllPlaces,
        };
        foreach (var entry in places.Others)
        {
            all.MenuItems.Add(Item(entry, places));
        }

        items.Add(all);
    }

    /// <summary>Marks the item of <paramref name=place/> as the one on show, wherever it sits now.</summary>
    public static void MarkActive(System.Collections.IList items, string place)
    {
        foreach (var item in items.OfType<NavigationViewItem>())
        {
            item.IsActive = Equals(item.Tag, place);
            foreach (var child in item.MenuItems.OfType<NavigationViewItem>())
            {
                child.IsActive = Equals(child.Tag, place);
            }
        }
    }

    private static NavigationViewItem Item(PlaceEntry entry, PlacesViewModel places)
    {
        var look = Places[entry.Id];
        var item = new NavigationViewItem
        {
            Content = entry.Label,
            Icon = new SymbolIcon { Symbol = look.Icon },
            TargetPageType = look.Page,
            Tag = entry.Id,
        };
        var pinned = places.IsPinned(entry.Id);
        var menu = new MenuItem
        {
            Header = Application.Current.FindResource(pinned ? "Places.UnpinFromSidebar" : "Places.PinToSidebar"),
            Icon = new SymbolIcon { Symbol = pinned ? SymbolRegular.PinOff24 : SymbolRegular.Pin24 },
            // The last pin stays, so it offers nothing to do.
            IsEnabled = !(pinned && places.Pinned.Count <= 1),
        };
        menu.Click += (_, _) => places.Toggle(entry.Id);
        item.ContextMenu = new ContextMenu { Items = { menu } };
        return item;
    }
}

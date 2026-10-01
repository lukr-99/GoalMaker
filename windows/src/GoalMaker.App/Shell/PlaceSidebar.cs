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
/// All places, which opens the Places page and whose arrow folds out the rest. Every place offers Pin
/// or Unpin on a right click.
/// </summary>
internal static class PlaceSidebar
{
    /// <summary>The tag All places carries, so the shell can tell whether it is folded out.</summary>
    public const string AllPlaces = PlacesViewModel.AllPlaces;

    private static readonly Dictionary<string, Type> Places = new(StringComparer.Ordinal)
    {
        [PlaceRules.Today] = typeof(TodayPage),
        [PlaceRules.Tomorrow] = typeof(TomorrowPage),
        [PlaceRules.Inbox] = typeof(InboxPage),
        [PlaceRules.Calendar] = typeof(CalendarPage),
        [PlaceRules.Habits] = typeof(HabitsPage),
        [PlaceRules.Goals] = typeof(GoalsPage),
        [PlaceRules.Projects] = typeof(ProjectsPage),
        [PlaceRules.Reviews] = typeof(ReviewsPage),
        [PlaceRules.Stats] = typeof(StatsPage),
        [PlaceRules.Wants] = typeof(WantsPage),
        [PlaceRules.Tally] = typeof(TallyPage),
        [PlaceRules.Archive] = typeof(ArchivePage),
        [PlacesViewModel.AllPlaces] = typeof(PlacesPage),
        [PlacesViewModel.Settings] = typeof(SettingsPage),
        [PlacesViewModel.Activity] = typeof(ActivityPage),
        [PlacesViewModel.Areas] = typeof(AreasPage),
    };

    public static Type PageOf(string place) => Places.TryGetValue(place, out var page) ? page : typeof(TodayPage);

    /// <summary>The place a page is, or null for a page that is not one (a task, the review, Plan tomorrow).</summary>
    public static string? PlaceOf(Type page) => Places.FirstOrDefault(entry => entry.Value == page).Key;

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

        // All places stays even with every place pinned: it opens the Places page.
        var all = new AllPlacesItem(() => places.Open(AllPlaces))
        {
            Content = Application.Current.FindResource("Places.All"),
            Icon = new SymbolIcon { Symbol = PlaceIcons.Of(AllPlaces) },
            TargetPageType = typeof(PlacesPage),
            IsExpanded = allOpen,
            Tag = AllPlaces,
        };
        System.Windows.Automation.AutomationProperties.SetHelpText(all, (string)Application.Current.FindResource("Places.AllHelp"));
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
        var item = new NavigationViewItem
        {
            Content = entry.Label,
            Icon = new SymbolIcon { Symbol = PlaceIcons.Of(entry.Id) },
            TargetPageType = Places[entry.Id],
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

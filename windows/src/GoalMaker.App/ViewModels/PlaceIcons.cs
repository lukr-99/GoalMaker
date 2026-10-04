using GoalMaker.Core.Navigation;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>The icon each place and page wears, the same in the sidebar, Go to and the Places page.</summary>
public static class PlaceIcons
{
    public static SymbolRegular Of(string place) => place switch
    {
        PlaceRules.Today => SymbolRegular.CalendarToday24,
        PlaceRules.Tomorrow => SymbolRegular.CalendarArrowRight24,
        PlaceRules.Inbox => SymbolRegular.MailInbox24,
        PlaceRules.Calendar => SymbolRegular.CalendarLtr24,
        PlaceRules.Habits => SymbolRegular.ArrowRepeatAll24,
        PlaceRules.Goals => SymbolRegular.Flag24,
        PlaceRules.LifeGoals => SymbolRegular.Sparkle24,
        PlaceRules.Projects => SymbolRegular.Board24,
        PlaceRules.Reviews => SymbolRegular.BookOpen24,
        PlaceRules.Stats => SymbolRegular.DataTrending24,
        PlaceRules.Wants => SymbolRegular.ShoppingBag24,
        PlaceRules.Tally => SymbolRegular.Timer24,
        PlaceRules.Archive => SymbolRegular.Archive24,
        PlacesViewModel.AllPlaces => SymbolRegular.Apps24,
        PlacesViewModel.Activity => SymbolRegular.History24,
        PlacesViewModel.Areas => SymbolRegular.Tag24,
        _ => SymbolRegular.Settings24,
    };
}

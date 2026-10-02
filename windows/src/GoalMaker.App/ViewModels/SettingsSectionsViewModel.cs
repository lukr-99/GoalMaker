using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The sections of Settings by their ids in contracts/vectors/settings.json, in page order, and where
/// opening the page lands. The settings kit's page draws the cards, the section list and the hints;
/// this knows GoalMaker's part: the PC-only sections sit before Your data (docs/design/spec.md,
/// Settings), and an update the mark on the Settings item promised lands on Updates. Also the way from
/// Areas and tags to the full Areas page.
/// </summary>
public sealed partial class SettingsSectionsViewModel
{
    public const string Problems = "problems";
    public const string Account = "account";
    public const string Appearance = "appearance";
    public const string Planning = "planning";
    public const string Areas = "areas";
    public const string Claude = "claude";
    public const string QuickAdd = "quick-add";
    public const string Startup = "startup";
    public const string MiniWindows = "mini-windows";
    public const string Data = "data";
    public const string Updates = "updates";
    public const string About = "about";
    public const string Developer = "developer";

    private readonly Action openAreas;

    public SettingsSectionsViewModel(Action openAreas) => this.openAreas = openAreas;

    /// <summary>The sections only the PC has, which sit between Claude and Your data.</summary>
    public static IReadOnlyList<string> PcOnly { get; } = [QuickAdd, Startup, MiniWindows];

    /// <summary>
    /// Every section in page order: the contract's order with the PC-only ones before Your data.
    /// Problems shows only while there are some and Developer only in a dev build.
    /// </summary>
    public static IReadOnlyList<string> Order { get; } =
        [Problems, Account, Appearance, Planning, Areas, Claude, QuickAdd, Startup, MiniWindows, Data, Updates, About, Developer];

    /// <summary>
    /// Where opening Settings lands: on Updates, with the jump hint, while an update waits and nothing
    /// went wrong, since that is what the mark on the Settings item stands for then. Problems come
    /// first on the page anyway, so with some the page opens at the top. Null stays at the top.
    /// </summary>
    public static string? Landing(bool hasProblems, bool hasUpdate) => hasUpdate && !hasProblems ? Updates : null;

    [RelayCommand]
    private void OpenAreas() => openAreas();
}

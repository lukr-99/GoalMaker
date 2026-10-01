using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The section list on the left of Settings: which sections there are, in page order, which one is
/// being read as the page scrolls, and the jump to one. The page does the scrolling when
/// <see cref="JumpRequested"/> fires and reports where the sections are through <see cref="Follow"/>.
/// Also the way from the Areas and tags section to the full Areas page.
/// </summary>
public sealed partial class SettingsSectionsViewModel : ObservableObject
{
    public const string Problems = "problems";
    public const string Account = "account";
    public const string Appearance = "appearance";
    public const string Planning = "planning";
    public const string Areas = "areas";
    public const string Connector = "connector";
    public const string QuickAdd = "quick-add";
    public const string Backup = "backup";
    public const string Startup = "startup";
    public const string MiniWindows = "mini-windows";
    public const string Updates = "updates";
    public const string About = "about";
    public const string Developer = "developer";

    // Every section in page order, with the key of its title.
    private static readonly (string Id, string Title)[] All =
    [
        (Problems, "Problems.Title"),
        (Account, "Settings.Account"),
        (Appearance, "Settings.Appearance"),
        (Planning, "Settings.Planning"),
        (Areas, "Areas.Title"),
        (Connector, "Connector.Title"),
        (QuickAdd, "Settings.QuickAdd"),
        (Backup, "Settings.Backup"),
        (Startup, "Settings.Startup"),
        (MiniWindows, "Settings.MiniWindows"),
        (Updates, "Settings.Updates"),
        (About, "Settings.About"),
        (Developer, "Settings.Developer"),
    ];

    private readonly IStrings strings;
    private readonly bool devBuild;
    private readonly Action openAreas;
    private string? jumped;

    [ObservableProperty]
    private string? current;

    public SettingsSectionsViewModel(IStrings strings, bool devBuild, Action openAreas)
    {
        this.strings = strings;
        this.devBuild = devBuild;
        this.openAreas = openAreas;
        Show(hasProblems: false);
    }

    /// <summary>Asks the page to scroll to a section.</summary>
    public event EventHandler<string>? JumpRequested;

    public ObservableCollection<SettingsSectionViewModel> Items { get; } = [];

    /// <summary>The ids in page order: Problems only while there are some, Developer only in a dev build.</summary>
    public static IReadOnlyList<string> Visible(bool hasProblems, bool devBuild) =>
        [.. All.Select(section => section.Id).Where(id => id switch
        {
            Problems => hasProblems,
            Developer => devBuild,
            _ => true,
        })];

    /// <summary>
    /// The section being read at <paramref name="offset"/>: the last one whose top has reached the top
    /// of the page (within <paramref name="slack"/>), or the last one once the page can't scroll any
    /// further, since a short last section never reaches the top. A section the owner
    /// <paramref name="jumped"/> to stays current at the bottom while its top is still in view, so a
    /// jump to a short section near the end marks that one. The first one before any is measured.
    /// </summary>
    public static string? CurrentAt(
        IReadOnlyList<string> sections,
        IReadOnlyDictionary<string, double> tops,
        double offset,
        double maxOffset,
        double slack = 0,
        string? jumped = null)
    {
        var measured = sections.Where(tops.ContainsKey).ToList();
        if (measured.Count == 0)
        {
            return sections.FirstOrDefault();
        }

        if (maxOffset > 0 && offset >= maxOffset - 0.5)
        {
            return jumped is not null && measured.Contains(jumped) && tops[jumped] >= offset - slack ? jumped : measured[^1];
        }

        return measured.LastOrDefault(id => tops[id] <= offset + slack) ?? measured[0];
    }

    /// <summary>Builds the list again, for when problems come or go.</summary>
    public void Show(bool hasProblems)
    {
        var ids = Visible(hasProblems, devBuild);
        if (Items.Select(item => item.Id).SequenceEqual(ids))
        {
            return;
        }

        Items.Clear();
        foreach (var id in ids)
        {
            var title = strings.Get(All.First(section => section.Id == id).Title);
            Items.Add(new SettingsSectionViewModel(id, title, strings.Get("Settings.JumpTo", title), JumpTo));
        }

        Mark(Current is { } current && ids.Contains(current) ? current : ids[0]);
    }

    /// <summary>Marks the section and asks the page to scroll to it. An unknown id does nothing.</summary>
    public void JumpTo(string id)
    {
        if (Items.All(item => item.Id != id))
        {
            return;
        }

        jumped = id;
        Mark(id);
        JumpRequested?.Invoke(this, id);
    }

    /// <summary>The page scrolled: marks the section now being read.</summary>
    public void Follow(IReadOnlyDictionary<string, double> tops, double offset, double maxOffset, double slack = 24)
    {
        if (CurrentAt([.. Items.Select(item => item.Id)], tops, offset, maxOffset, slack, jumped) is { } id)
        {
            Mark(id);
        }
    }

    private void Mark(string id)
    {
        Current = id;
        foreach (var item in Items)
        {
            item.IsCurrent = item.Id == id;
        }
    }

    [RelayCommand]
    private void OpenAreas() => openAreas();
}

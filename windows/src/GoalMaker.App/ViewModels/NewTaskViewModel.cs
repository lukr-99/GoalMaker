using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The new task form the empty bar's plus opens on Today, Tomorrow and the Inbox (docs/composer.md):
/// the title, the day (the list's own, tomorrow or none), the area, top priority and notes. Ctrl+N
/// opens it filled in with what the line says; the parts the form has no field for (a time, tags, a
/// project, a repeat) come along unchanged. The rest waits in the task's details.
/// </summary>
public sealed partial class NewTaskViewModel : ObservableObject
{
    private const string NoDay = "none";
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly IStrings strings;
    private readonly Func<DateOnly> today;
    private ComposerDraft basis = Empty;
    private Action? onAdded;

    [ObservableProperty]
    private bool isOpen;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private string title = string.Empty;

    [ObservableProperty]
    private string notes = string.Empty;

    [ObservableProperty]
    private bool topPriority;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> days = [];

    [ObservableProperty]
    private ChoiceViewModel? day;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> areaChoices = [];

    [ObservableProperty]
    private ChoiceViewModel? area;

    public NewTaskViewModel(TaskList tasks, AreaList areas, IStrings strings, Func<DateOnly> today)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.strings = strings;
        this.today = today;
    }

    private static ComposerDraft Empty => new(string.Empty, null, null, [], null, null, false, false, null, null, []);

    /// <summary>
    /// Opens the form with what a line says (null: blank) on a list whose own day is
    /// <paramref name="listDay"/> (null for the Inbox); <paramref name="added"/> runs once it is saved.
    /// </summary>
    public void Open(ComposerDraft? draft, DateOnly? listDay, Action? added)
    {
        basis = draft ?? Empty;
        onAdded = added;
        var now = today();
        var planned = draft is null ? listDay : draft.PlannedDate;
        var choices = new List<ChoiceViewModel>
        {
            new(Id(now), strings.Get("Composer.Today")),
            new(Id(now.AddDays(1)), strings.Get("Composer.Tomorrow")),
            new(NoDay, strings.Get("NewTask.NoDay")),
        };
        if (planned is { } other && other != now && other != now.AddDays(1))
        {
            choices.Insert(2, new ChoiceViewModel(Id(other), other.ToString("ddd d MMM", CultureInfo.CurrentCulture)));
        }

        Days = choices;
        Day = choices.First(choice => choice.Id == (planned is { } date ? Id(date) : NoDay));

        var areaList = new List<ChoiceViewModel> { new(null, strings.Get("NewTask.NoArea")) };
        areaList.AddRange(areas.Active().Select(one => new ChoiceViewModel(one.Name, one.Name)));
        if (draft?.Area is { } named && areas.Find(named) is null)
        {
            areaList.Add(new ChoiceViewModel(named, strings.Get("NewTask.NewArea", named)));
        }

        AreaChoices = areaList;
        Area = areaList.FirstOrDefault(choice => choice.Id is { } id && draft?.Area is { } name && string.Equals(id, areas.Find(name)?.Name ?? name, StringComparison.OrdinalIgnoreCase))
            ?? areaList[0];
        Title = draft?.Title ?? string.Empty;
        Notes = string.Empty;
        TopPriority = draft?.TopPriority ?? false;
        IsOpen = true;
    }

    private bool CanSave() => Title.Trim().Length > 0;

    [RelayCommand(CanExecute = nameof(CanSave))]
    private void Save()
    {
        var planned = Day?.Id is { } id && id != NoDay ? DateOnly.ParseExact(id, "yyyy-MM-dd", CultureInfo.InvariantCulture) : (DateOnly?)null;
        var draft = basis with
        {
            Title = Title.Trim(),
            PlannedDate = planned,
            Area = Area?.Id,
            TopPriority = TopPriority,
        };
        if (tasks.Add(draft, Notes.Trim()) is null)
        {
            return;
        }

        IsOpen = false;
        onAdded?.Invoke();
        onAdded = null;
    }

    [RelayCommand]
    private void Cancel()
    {
        onAdded = null;
        IsOpen = false;
    }

    private static string Id(DateOnly date) => date.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
}

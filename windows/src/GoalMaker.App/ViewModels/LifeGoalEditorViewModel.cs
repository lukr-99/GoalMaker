using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Adds a life goal or edits one (docs/life-goals.md): the title and the why are required; the by date
/// is In 5, 10 or 20 years, a picked day or none. Pictures come from a file picker or are dropped on
/// the editor, are shrunk as they come, and are kept, with the ones removed, only when it saves.
/// </summary>
public sealed partial class LifeGoalEditorViewModel : ObservableObject
{
    /// <summary>The by choice that shows the day picker.</summary>
    public const string PickDay = "day";

    private const int ThumbWidth = 200;
    private readonly LifeGoalList lifeGoals;
    private readonly LifeGoalPictures pictures;
    private readonly IStrings strings;
    private readonly Func<DateOnly> today;
    private readonly Func<string, ShrunkPicture?> shrink;
    private readonly Func<IReadOnlyList<string>> pick;
    private readonly List<string> removed = [];
    private LifeGoalItem? editing;

    [ObservableProperty]
    private bool isOpen;

    [ObservableProperty]
    private string heading = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private string title = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private string why = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PicksDay))]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private ChoiceViewModel? by;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private DateTime? pickedDay;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasPictureProblem))]
    private string pictureProblem = string.Empty;

    [ObservableProperty]
    private bool isAdding;

    /// <param name="shrink">Makes a picture file ready to keep; null when it can't be read. It may block, so it runs off the UI thread.</param>
    /// <param name="pick">Asks the owner for picture files; empty when they cancel.</param>
    public LifeGoalEditorViewModel(
        LifeGoalList lifeGoals,
        LifeGoalPictures pictures,
        IStrings strings,
        Func<DateOnly> today,
        Func<string, ShrunkPicture?> shrink,
        Func<IReadOnlyList<string>> pick)
    {
        this.lifeGoals = lifeGoals;
        this.pictures = pictures;
        this.strings = strings;
        this.today = today;
        this.shrink = shrink;
        this.pick = pick;
        ByChoices =
        [
            new ChoiceViewModel(null, strings.Get("LifeGoals.ByNone")),
            .. LifeGoalRules.ByYears.Select(years => new ChoiceViewModel(years.ToString(CultureInfo.InvariantCulture), strings.Get("LifeGoals.InYears", years))),
            new ChoiceViewModel(PickDay, strings.Get("LifeGoals.PickDay")),
        ];
        by = ByChoices[0];
    }

    /// <summary>Saved: the page shows the life goal and its pictures.</summary>
    public event EventHandler? Saved;

    public IReadOnlyList<ChoiceViewModel> ByChoices { get; }

    /// <summary>The pictures the life goal will have: the kept ones still there, then the ones added here.</summary>
    public ObservableCollection<LifeGoalPictureViewModel> Pictures { get; } = [];

    public bool PicksDay => By?.Id == PickDay;

    public bool HasPictureProblem => PictureProblem.Length > 0;

    /// <summary>The first day the day picker offers: tomorrow.</summary>
    public DateTime FirstDay => today().AddDays(1).ToDateTime(TimeOnly.MinValue);

    /// <summary>The by date the choice gives, or null for none.</summary>
    public DateOnly? ByDate => By?.Id switch
    {
        null => null,
        PickDay => PickedDay is { } day ? DateOnly.FromDateTime(day) : null,
        var years => today().AddYears(int.Parse(years, CultureInfo.InvariantCulture)),
    };

    /// <summary>Opens the editor on <paramref name="goal"/> with its <paramref name="kept"/> pictures, or blank for a new one.</summary>
    public void Open(LifeGoalItem? goal, IReadOnlyList<LifeGoalPicture> kept)
    {
        editing = goal;
        removed.Clear();
        IsAdding = goal is null;
        Heading = strings.Get(goal is null ? "LifeGoals.Add" : "LifeGoals.EditTitle");
        Title = goal?.Title ?? string.Empty;
        Why = goal?.Why ?? string.Empty;
        var start = today();
        var years = goal?.By is { } day ? LifeGoalRules.ByYears.Where(count => start.AddYears(count) == day).Select(count => (int?)count).FirstOrDefault() : null;
        By = goal?.By is null ? ByChoices[0]
            : years is { } count ? ByChoices.First(choice => choice.Id == count.ToString(CultureInfo.InvariantCulture))
            : ByChoices[^1];
        PickedDay = years is null && goal?.By is { } picked ? picked.ToDateTime(TimeOnly.MinValue) : null;
        PictureProblem = string.Empty;
        Pictures.Clear();
        foreach (var picture in kept)
        {
            Pictures.Add(new LifeGoalPictureViewModel(
                picture.Id, null, strings.Get("LifeGoals.Pictures"), () => pictures.Read(picture.Id), ThumbWidth, Remove));
        }

        OnPropertyChanged(nameof(FirstDay));
        IsOpen = true;
    }

    /// <summary>Shrinks each file and adds it; one that can't be read as a picture says so and the rest still come.</summary>
    public async Task AddFilesAsync(IEnumerable<string> paths)
    {
        PictureProblem = string.Empty;
        foreach (var path in paths)
        {
            var shrunk = await Task.Run(() => shrink(path)).ConfigureAwait(true);
            if (shrunk is null)
            {
                PictureProblem = strings.Get("LifeGoals.PictureFailed");
                continue;
            }

            Pictures.Add(new LifeGoalPictureViewModel(null, shrunk, strings.Get("LifeGoals.Pictures"), () => shrunk.Jpeg, ThumbWidth, Remove));
        }
    }

    /// <summary>
    /// Keeps the life goal, then the pictures added and removed here. Null when it could not be saved
    /// (no title or no why, or the life goal is gone).
    /// </summary>
    public LifeGoalItem? Keep()
    {
        if (!CanSave())
        {
            return null;
        }

        var draft = new LifeGoalDraft(Title, Why, ByDate, editing?.AreaId);
        var goal = editing is null ? lifeGoals.Add(draft) : lifeGoals.Update(editing.Id, draft) ? lifeGoals.Get(editing.Id) : null;
        if (goal is null)
        {
            return null;
        }

        foreach (var id in removed)
        {
            lifeGoals.RemovePicture(id);
        }

        foreach (var added in Pictures.Select(picture => picture.Added).OfType<ShrunkPicture>())
        {
            pictures.Add(goal.Id, added.Jpeg, added.Width, added.Height);
        }

        IsOpen = false;
        Saved?.Invoke(this, EventArgs.Empty);
        return goal;
    }

    private bool CanSave() => Title.Trim().Length > 0 && Why.Trim().Length > 0 && !(PicksDay && PickedDay is null);

    [RelayCommand(CanExecute = nameof(CanSave))]
    private void Save() => Keep();

    [RelayCommand]
    private void Cancel() => IsOpen = false;

    [RelayCommand]
    private Task AddPicturesAsync() => AddFilesAsync(pick());

    private void Remove(LifeGoalPictureViewModel picture)
    {
        if (Pictures.Remove(picture) && picture.Id is { } id)
        {
            removed.Add(id);
        }
    }
}

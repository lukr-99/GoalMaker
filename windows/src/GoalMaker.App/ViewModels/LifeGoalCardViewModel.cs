using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One life goal on the Life goals page (docs/life-goals.md, "On screen"): its pictures to click
/// through, or its first letter when it has none; the title, the why in full and the time left. The
/// menu edits it, marks it achieved, drops, moves, reopens or deletes it.
/// </summary>
public sealed partial class LifeGoalCardViewModel : ObservableObject
{
    private readonly LifeGoalsViewModel page;
    private readonly Action<string, int> remember;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(Shown), nameof(Dots))]
    private int shownIndex;

    public LifeGoalCardViewModel(
        LifeGoalsViewModel page,
        LifeGoalItem goal,
        string status,
        IReadOnlyList<LifeGoalPictureViewModel> pictures,
        bool canMoveUp,
        bool canMoveDown,
        string menuName,
        int shown,
        Action<string, int> remember)
    {
        this.page = page;
        this.remember = remember;
        Goal = goal;
        Status = status;
        Pictures = pictures;
        CanMoveUp = canMoveUp;
        CanMoveDown = canMoveDown;
        MenuName = menuName;
        shownIndex = pictures.Count == 0 ? 0 : Math.Clamp(shown, 0, pictures.Count - 1);
    }

    public LifeGoalItem Goal { get; }

    public string Title => Goal.Title;

    public string Why => Goal.Why;

    /// <summary>The time left, or achieved or dropped, and "by Claude" when Claude added it.</summary>
    public string Status { get; }

    public bool HasStatus => Status.Length > 0;

    public bool IsOpen => Goal.Status == LifeGoalRules.Open;

    public bool IsClosed => !IsOpen;

    public bool CanMoveUp { get; }

    public bool CanMoveDown { get; }

    public IReadOnlyList<LifeGoalPictureViewModel> Pictures { get; }

    /// <summary>An open life goal shows its pictures, or its first letter on the accent when it has none.</summary>
    public bool ShowsPictures => IsOpen && Pictures.Count > 0;

    public bool ShowsLetter => IsOpen && Pictures.Count == 0;

    public string Letter => Title.Length == 0 ? string.Empty : Title[..1].ToUpper(System.Globalization.CultureInfo.CurrentCulture);

    public bool HasManyPictures => Pictures.Count > 1;

    public LifeGoalPictureViewModel? Shown => Pictures.Count == 0 ? null : Pictures[ShownIndex];

    /// <summary>One dot per picture, the one on show at full strength.</summary>
    public IReadOnlyList<double> Dots => [.. Pictures.Select((_, index) => index == ShownIndex ? 1.0 : 0.45)];

    /// <summary>The "more" button's name: More for Own an Audi R8.</summary>
    public string MenuName { get; }

    /// <summary>What a screen reader says for the card: its title and where it stands.</summary>
    public string CardName => HasStatus ? Title + ", " + Status : Title;

    [RelayCommand]
    private void Edit() => page.Edit(this);

    [RelayCommand]
    private void Achieve() => page.Achieve(this);

    [RelayCommand]
    private void Drop() => page.Drop(this);

    [RelayCommand]
    private void Reopen() => page.Reopen(this);

    [RelayCommand]
    private void MoveUp() => page.Move(this, -1);

    [RelayCommand]
    private void MoveDown() => page.Move(this, 1);

    [RelayCommand]
    private void Delete() => page.AskDelete(this);

    [RelayCommand]
    private void NextPicture() => Turn(1);

    [RelayCommand]
    private void PreviousPicture() => Turn(-1);

    // Clicking through goes round: after the last picture comes the first.
    private void Turn(int step)
    {
        if (Pictures.Count < 2)
        {
            return;
        }

        ShownIndex = (ShownIndex + step + Pictures.Count) % Pictures.Count;
        remember(Goal.Id, ShownIndex);
    }
}

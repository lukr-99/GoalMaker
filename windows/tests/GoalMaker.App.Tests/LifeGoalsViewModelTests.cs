using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Life goals page over a real replica: order, time left, the editor's pictures, undo, moving (M9-03).</summary>
public sealed class LifeGoalsViewModelTests : IDisposable
{
    // Friday 18 September 2026, the planning day the by dates count from.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private readonly MemoryFiles files = new();
    private readonly LifeGoalPictures pictures;
    private readonly Dictionary<string, ShrunkPicture> pictureFiles = new(StringComparer.Ordinal)
    {
        ["car.jpg"] = new ShrunkPicture([1, 2, 3], 1600, 900),
        ["side.png"] = new ShrunkPicture([4, 5], 800, 450),
    };

    private IReadOnlyList<string> picked = [];

    public LifeGoalsViewModelTests() =>
        pictures = new LifeGoalPictures(planner.LifeGoals, files, new NoCloud(), () => TestPlanner.Owner, planner.Time, () => { });

    public void Dispose() => planner.Dispose();

    private LifeGoalsViewModel Page() => new(
        planner.LifeGoals, pictures, planner.Settings, planner.Strings, planner.Time, action => action(), pictureFiles.GetValueOrDefault, () => picked);

    [Fact]
    public void OpenOnesComeInOrderWithTheirTimeLeftAndClosedOnesFoldBelow()
    {
        var car = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof", Today.AddYears(10)))!;
        var run = planner.LifeGoals.Add(new LifeGoalDraft("Run a marathon", "To know I can"))!;
        planner.LifeGoals.Add(new LifeGoalDraft("See the aurora", "Wonder", new DateOnly(2027, 5, 18)));
        planner.LifeGoals.Add(new LifeGoalDraft("Learn to sail", "The sea", new DateOnly(2026, 9, 30)));
        planner.LifeGoals.Add(new LifeGoalDraft("Visit Japan", "Food", Today));
        var boat = planner.LifeGoals.Add(new LifeGoalDraft("Buy a boat", "Freedom"))!;
        planner.LifeGoals.Drop(boat.Id);
        // Claude added the marathon through the connector, and that is how it arrives from the server.
        var row = planner.Replica.Get("life_goals", run.Id)!;
        row["made_by"] = ProjectRules.Claude;
        planner.Replica.Put("life_goals", row);

        var page = Page();

        Assert.Equal(
            ["Own an Audi R8", "Run a marathon", "See the aurora", "Learn to sail", "Visit Japan"],
            page.Open.Select(card => card.Title));
        Assert.Equal(
            ["LifeGoals.YearsLeft(10)", "LifeGoals.ByClaude", "LifeGoals.MonthsLeft(8)", "LifeGoals.DaysLeft(12)", "LifeGoals.Today"],
            page.Open.Select(card => card.Status));
        Assert.Equal(car.Id, page.Open[0].Goal.Id);
        Assert.True(page.Open[0].ShowsLetter);
        Assert.Equal("O", page.Open[0].Letter);
        Assert.False(page.Open[0].CanMoveUp);
        Assert.True(page.Open[^1].CanMoveUp);
        Assert.False(page.Open[^1].CanMoveDown);
        var closed = Assert.Single(page.Closed);
        Assert.Equal("LifeGoals.StatusDropped", closed.Status);
        Assert.True(closed.IsClosed);
        Assert.Equal("LifeGoals.Closed(1)", page.ClosedLabel);
        Assert.True(page.HasClosed);
        Assert.False(page.ShowsClosed);
        page.ToggleClosedCommand.Execute(null);
        Assert.True(page.ShowsClosed);
        Assert.False(page.IsEmpty);
    }

    [Fact]
    public void TimeLeftSaysOneInTheSingularAndAPastDateSaysSo()
    {
        var strings = planner.Strings;

        Assert.Equal("LifeGoals.YearLeft(1)", LifeGoalsViewModel.TimeLeftText(new TimeLeft(TimeLeftUnit.Years, 1), strings));
        Assert.Equal("LifeGoals.MonthLeft(1)", LifeGoalsViewModel.TimeLeftText(new TimeLeft(TimeLeftUnit.Months, 1), strings));
        Assert.Equal("LifeGoals.DayLeft(1)", LifeGoalsViewModel.TimeLeftText(new TimeLeft(TimeLeftUnit.Days, 1), strings));
        Assert.Equal("LifeGoals.Past", LifeGoalsViewModel.TimeLeftText(new TimeLeft(TimeLeftUnit.Past, 0), strings));
    }

    [Fact]
    public async Task SavingTheEditorKeepsTheLifeGoalAndItsAddedPicturesAndDropsTheRemovedOnes()
    {
        var page = Page();
        Assert.True(page.IsEmpty);
        page.AddCommand.Execute(null);
        var editor = page.Editor;
        Assert.True(editor.IsOpen);
        Assert.Equal("LifeGoals.Add", editor.Heading);

        editor.Title = "Own an Audi R8";
        Assert.False(editor.SaveCommand.CanExecute(null));
        editor.Why = "Proof that the work paid off";
        editor.By = editor.ByChoices.Single(choice => choice.Id == "10");
        await editor.AddFilesAsync(["car.jpg", "notes.txt"]);
        Assert.Equal("LifeGoals.PictureFailed", editor.PictureProblem);
        Assert.Single(editor.Pictures);
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var goal = Assert.Single(planner.LifeGoals.All());
        Assert.Equal(Today.AddYears(10), goal.By);
        var first = Assert.Single(planner.LifeGoals.PicturesOf(goal.Id));
        Assert.Equal([1, 2, 3], pictures.Read(first.Id));
        Assert.Equal([first.Id], files.Pending());
        var card = Assert.Single(page.Open);
        Assert.True(card.ShowsPictures);
        Assert.Equal("LifeGoals.Picture(1,1,Own an Audi R8)", card.Shown!.Name);

        card.EditCommand.Execute(null);
        Assert.Equal("LifeGoals.EditTitle", editor.Heading);
        Assert.Equal("10", editor.By!.Id);
        editor.Pictures.Single().RemoveCommand.Execute(null);
        picked = ["side.png"];
        await editor.AddPicturesCommand.ExecuteAsync(null);
        editor.Title = "Own an Audi R8 Spyder";
        editor.SaveCommand.Execute(null);

        Assert.Equal("Own an Audi R8 Spyder", planner.LifeGoals.Get(goal.Id)!.Title);
        Assert.Equal([800], planner.LifeGoals.PicturesOf(goal.Id).Select(picture => picture.Width));
    }

    [Fact]
    public void AByDateThatIsNoneOfTheOffersOpensOnPickADay()
    {
        planner.LifeGoals.Add(new LifeGoalDraft("Learn to sail", "The sea", new DateOnly(2030, 6, 1)));
        var page = Page();

        page.Open[0].EditCommand.Execute(null);

        Assert.True(page.Editor.PicksDay);
        Assert.Equal(new DateTime(2030, 6, 1), page.Editor.PickedDay);
        page.Editor.PickedDay = null;
        Assert.False(page.Editor.SaveCommand.CanExecute(null));
        page.Editor.By = page.Editor.ByChoices[0];
        page.Editor.SaveCommand.Execute(null);

        Assert.Null(planner.LifeGoals.All()[0].By);
    }

    [Fact]
    public void AchievingOffersAnUndoThatReopensItAndMovingSwapsNeighbours()
    {
        var a = planner.LifeGoals.Add(new LifeGoalDraft("A", "a"))!;
        var b = planner.LifeGoals.Add(new LifeGoalDraft("B", "b"))!;
        var page = Page();

        page.Open[1].MoveUpCommand.Execute(null);
        Assert.Equal([b.Id, a.Id], planner.LifeGoals.All().Select(goal => goal.Id));
        Assert.Equal(["B", "A"], page.Open.Select(card => card.Title));

        page.Open[1].AchieveCommand.Execute(null);

        Assert.Equal(LifeGoalRules.Achieved, planner.LifeGoals.Get(a.Id)!.Status);
        Assert.True(page.HasUndo);
        Assert.Equal("LifeGoals.AchievedMessage(A)", page.UndoText);
        Assert.Equal(["A"], page.Closed.Select(card => card.Title));

        page.UndoCommand.Execute(null);

        Assert.Equal(LifeGoalRules.Open, planner.LifeGoals.Get(a.Id)!.Status);
        Assert.False(page.HasUndo);
    }

    [Fact]
    public void DeletingAsksFirstAndUndoBringsItBackWithItsPictures()
    {
        var car = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        pictures.Add(car.Id, [1, 2, 3], 1600, 900);
        var page = Page();

        page.Open[0].DeleteCommand.Execute(null);
        Assert.True(page.IsConfirmingDelete);
        Assert.Equal("LifeGoals.DeleteQuestion(Own an Audi R8)", page.DeleteQuestion);
        page.CancelDeleteCommand.Execute(null);
        Assert.NotNull(planner.LifeGoals.Get(car.Id));

        page.Open[0].DeleteCommand.Execute(null);
        page.ConfirmDeleteCommand.Execute(null);

        Assert.False(page.IsConfirmingDelete);
        Assert.Null(planner.LifeGoals.Get(car.Id));
        Assert.Empty(page.Open);
        Assert.Equal("LifeGoals.DeletedMessage(Own an Audi R8)", page.UndoText);

        page.UndoCommand.Execute(null);

        Assert.Single(planner.LifeGoals.PicturesOf(car.Id));
        Assert.Single(page.Open);
    }

    [Fact]
    public void ACardGoesRoundItsPicturesAndKeepsItsPlaceWhenThePageIsReadAgain()
    {
        var car = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        pictures.Add(car.Id, [1], 1600, 900);
        pictures.Add(car.Id, [2], 1600, 900);
        var page = Page();
        var card = page.Open[0];
        Assert.True(card.HasManyPictures);
        Assert.Equal([1.0, 0.45], card.Dots);

        card.PreviousPictureCommand.Execute(null);
        Assert.Equal(1, card.ShownIndex);
        page.Refresh();

        Assert.Equal(1, page.Open[0].ShownIndex);
        Assert.Equal("LifeGoals.Picture(2,2,Own an Audi R8)", page.Open[0].Shown!.Name);
    }

    private sealed class MemoryFiles : IPictureFiles
    {
        private readonly Dictionary<string, byte[]> bytes = [];
        private readonly HashSet<string> waiting = [];

        public bool Has(string id) => bytes.ContainsKey(id);

        public byte[]? Read(string id) => bytes.GetValueOrDefault(id);

        public void Write(string id, byte[] bytes, bool pending)
        {
            this.bytes[id] = bytes;
            if (pending)
            {
                waiting.Add(id);
            }
        }

        public void Delete(string id)
        {
            bytes.Remove(id);
            waiting.Remove(id);
        }

        public IReadOnlySet<string> Ids() => bytes.Keys.ToHashSet();

        public IReadOnlySet<string> Pending() => waiting.ToHashSet();

        public void Uploaded(string id) => waiting.Remove(id);
    }

    private sealed class NoCloud : IPictureCloud
    {
        public Task UploadAsync(string owner, string id, byte[] bytes, CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<byte[]?> DownloadAsync(string owner, string id, CancellationToken cancellationToken) => Task.FromResult<byte[]?>(null);

        public Task RemoveAsync(string owner, string id, CancellationToken cancellationToken) => Task.CompletedTask;
    }
}

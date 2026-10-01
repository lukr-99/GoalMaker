using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Assistant;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Tests;

/// <summary>
/// The bottom bar on Today, Wants, Habits and Goals (docs/composer.md): the plus while the line is empty
/// opens the page's form, the send arrow once it is typed adds the item, what a line can't add opens the
/// form filled in, and the quick chat takes the line on every page.
/// </summary>
public sealed class BottomBarTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private readonly FakeAssistant assistant = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheButtonIsAPlusWhileEmptyAndTheSendArrowOnceTyped()
    {
        var bar = Wants().Bar;

        Assert.True(bar.ShowsPlus);
        Assert.False(bar.ShowsSend);
        Assert.Equal("Wants.New", bar.ButtonName);
        Assert.True(bar.PressCommand.CanExecute(null));
        Assert.False(bar.SendCommand.CanExecute(null));

        bar.Line = "Kindle";

        Assert.False(bar.ShowsPlus);
        Assert.True(bar.ShowsSend);
        Assert.Equal("Wants.BarAdd", bar.ButtonName);
        Assert.True(bar.SendCommand.CanExecute(null));

        bar.Line = "   ";
        Assert.True(bar.ShowsPlus);
    }

    [Fact]
    public void ThePlusOpensTheBlankForm()
    {
        var wants = Wants();
        wants.Bar.PressCommand.Execute(null);
        Assert.True(wants.IsEditing);
        Assert.True(wants.IsAdding);
        Assert.Equal(string.Empty, wants.DraftTitle);

        var habits = Habits();
        habits.Bar.PressCommand.Execute(null);
        Assert.True(habits.Editor.IsOpen);
        Assert.Equal(string.Empty, habits.Editor.Name);

        var goals = Goals();
        goals.Bar.PressCommand.Execute(null);
        Assert.True(goals.Editor.IsOpen);
        Assert.Equal(GoalRules.Id(GoalHorizon.Week), goals.Editor.Horizon?.Id);
    }

    [Fact]
    public async Task AWantLineAddsTheWantWithItsPriceWaitAndReason()
    {
        var wants = Wants();
        wants.Bar.Line = "Kindle 3 290 Kč wait 2 weeks because I read on the train";

        Assert.Equal(["Wants.Money", "Wants.BarWaits(14)", "Wants.BarWhy(I read on the train)"], wants.Bar.Chips.Select(Plain));
        await wants.Bar.PressCommand.ExecuteAsync(null);

        var want = Assert.Single(planner.Wants.All());
        Assert.Equal("Kindle", want.Title);
        Assert.Equal("I read on the train", want.Reason);
        Assert.Equal(3290, want.Price);
        Assert.Equal("CZK", want.Currency);
        Assert.Equal(Today.AddDays(14), want.CoolsUntil);
        Assert.Equal(string.Empty, wants.Bar.Line);
        Assert.False(wants.IsEditing);
    }

    [Fact]
    public async Task AWantWithoutAReasonOpensThePanelFilledInAndKeepsTheLineUntilItSaves()
    {
        var wants = Wants();
        wants.Bar.Line = "Headphones 2490 Kč";
        Assert.True(wants.Bar.Chips.Last().IsWarning);

        await wants.Bar.SendCommand.ExecuteAsync(null);

        Assert.Empty(planner.Wants.All());
        Assert.True(wants.IsEditing);
        Assert.Equal("Headphones", wants.DraftTitle);
        Assert.Equal(2490, WantsViewModel.ParseMoney(wants.DraftPrice));
        Assert.Equal("Headphones 2490 Kč", wants.Bar.Line);

        wants.DraftReason = "The old ones broke";
        wants.SaveDraftCommand.Execute(null);

        Assert.Equal("The old ones broke", Assert.Single(planner.Wants.All()).Reason);
        Assert.Equal(string.Empty, wants.Bar.Line);
    }

    [Fact]
    public async Task AHabitLineAddsTheHabitWithItsCadenceAndMeasure()
    {
        var habits = Habits();
        habits.Bar.Line = "Swim 2 times a week 40 min";

        Assert.Equal(["Habits.TimesWeek(2)", "Habits.BarTargetUnit(40,min)"], habits.Bar.Chips.Select(chip => chip.Label));
        await habits.Bar.PressCommand.ExecuteAsync(null);

        var habit = Assert.Single(planner.Habits.All());
        Assert.Equal("Swim", habit.Name);
        Assert.Equal(HabitRules.PerWeek, habit.Cadence);
        Assert.Equal(2, habit.Times);
        Assert.Equal(HabitRules.Amount, habit.Measure);
        Assert.Equal(40, habit.Target);
        Assert.Equal("min", habit.Unit);
        Assert.Equal(Today, habit.StartsOn);
        Assert.Equal(string.Empty, habits.Bar.Line);
    }

    [Fact]
    public async Task AHabitLineWithNoNameOpensTheEditorFilledIn()
    {
        var habits = Habits();
        habits.Bar.Line = "every mon and thu";

        await habits.Bar.SendCommand.ExecuteAsync(null);

        Assert.Empty(planner.Habits.All());
        Assert.True(habits.Editor.IsOpen);
        Assert.Equal(HabitRules.OnWeekdays, habits.Editor.Cadence?.Id);
        Assert.Equal([0, 3], habits.Editor.Days.Where(day => day.IsChosen).Select(day => day.Index));

        habits.Editor.Name = "Piano";
        habits.Editor.SaveCommand.Execute(null);
        Assert.Equal(9, Assert.Single(planner.Habits.All()).Weekdays);
        Assert.Equal(string.Empty, habits.Bar.Line);
    }

    [Fact]
    public async Task AGoalLineAddsTheGoalForItsPeriodWithItsTarget()
    {
        var goals = Goals();
        goals.Bar.Line = "Read 3 books this month";

        Assert.Equal(["Goals.ThisMonth", "Goals.BarTarget(3,books)"], goals.Bar.Chips.Select(chip => chip.Label));
        await goals.Bar.PressCommand.ExecuteAsync(null);

        var goal = Assert.Single(planner.Goals.All());
        Assert.Equal("Read 3 books", goal.Title);
        Assert.Equal(GoalHorizon.Month, goal.Horizon);
        Assert.Equal(new DateOnly(2026, 9, 1), goal.PeriodStart);
        Assert.Equal(GoalRules.ModeNumber, goal.Mode);
        Assert.Equal(3, goal.Target);
        Assert.Equal("books", goal.Unit);
    }

    [Fact]
    public void CtrlNOpensTheFormWithTheLineSoFar()
    {
        var goals = Goals();
        goals.Bar.Line = "Ski trip in February";

        goals.Bar.OpenFormCommand.Execute(null);

        Assert.True(goals.Editor.IsOpen);
        Assert.Equal("Ski trip", goals.Editor.Title);
        Assert.Equal(GoalRules.Id(GoalHorizon.Month), goals.Editor.Horizon?.Id);
        Assert.Equal("2027-02-01", goals.Editor.Period?.Id);
        Assert.Equal("Ski trip in February", goals.Bar.Line);

        goals.Editor.SaveCommand.Execute(null);
        Assert.Equal(new DateOnly(2027, 2, 1), Assert.Single(planner.Goals.All()).PeriodStart);
        Assert.Equal(string.Empty, goals.Bar.Line);
    }

    [Fact]
    public void ACancelledFormLeavesTheLine()
    {
        var habits = Habits();
        habits.Bar.Line = "every day";
        habits.Bar.OpenFormCommand.Execute(null);

        habits.Editor.CancelCommand.Execute(null);

        Assert.Equal("every day", habits.Bar.Line);
    }

    [Fact]
    public async Task InTheQuickChatTheButtonIsAlwaysSendAndTheLineGoesToTheChat()
    {
        planner.Settings.ComposerMode = ComposerMode.Chat;
        var chat = new ChatViewModel(assistant, new FakeAuth(), planner.Sync, planner.Settings, planner.Strings, planner.Time, action => action(), () => { });
        var habits = Habits(chat);

        Assert.True(habits.Bar.IsChat);
        Assert.True(habits.Bar.ShowsSend);
        Assert.Equal("Chat.Send", habits.Bar.ButtonName);
        Assert.False(habits.Bar.PressCommand.CanExecute(null));

        assistant.Replies.Enqueue(new AssistantReply.Answer("Added Swim."));
        habits.Bar.Line = "add swim twice a week";
        Assert.False(habits.Bar.HasChips);
        await habits.Bar.PressCommand.ExecuteAsync(null);

        Assert.Empty(planner.Habits.All());
        Assert.Equal(2, chat.Lines.Count);
        Assert.Equal(string.Empty, habits.Bar.Line);
        Assert.False(habits.Editor.IsOpen);

        chat.SwitchCommand.Execute(null);
        Assert.True(habits.Bar.ShowsPlus);
        Assert.Equal("Habits.New", habits.Bar.ButtonName);
    }

    [Fact]
    public void TodaysPlusOpensTheNewTaskFormOnTheListsDay()
    {
        var today = List(ListKind.Today, day => day);
        Assert.True(today.Composer.ShowsPlus);
        Assert.Equal("Composer.NewTask", today.Composer.ButtonName);

        today.Composer.PressCommand.Execute(null);

        Assert.True(today.NewTask.IsOpen);
        Assert.Equal("Composer.Today", today.NewTask.Day?.Label);
        Assert.False(today.NewTask.SaveCommand.CanExecute(null));

        today.NewTask.Title = "Call the plumber";
        today.NewTask.TopPriority = true;
        today.NewTask.Notes = "Ask about the boiler too.";
        today.NewTask.Day = today.NewTask.Days.Single(choice => choice.Label == "Composer.Tomorrow");
        today.NewTask.SaveCommand.Execute(null);

        var task = planner.Task("Call the plumber");
        Assert.Equal(Today.AddDays(1), task.PlannedDate);
        Assert.True(task.TopPriority);
        Assert.Equal("Ask about the boiler too.", task.Notes);
        Assert.False(today.NewTask.IsOpen);
    }

    [Fact]
    public void TheInboxFormStartsWithNoDayAndCtrlNKeepsWhatTheLineSays()
    {
        var inbox = List(ListKind.Inbox, _ => null);
        inbox.Composer.PressCommand.Execute(null);
        Assert.Equal("NewTask.NoDay", inbox.NewTask.Day?.Label);
        inbox.NewTask.CancelCommand.Execute(null);

        inbox.Composer.NewTaskTitle = "Buy shoes tomorrow 9:00 #run @Errands !";
        inbox.Composer.OpenFormCommand.Execute(null);

        Assert.Equal("Buy shoes", inbox.NewTask.Title);
        Assert.Equal("Composer.Tomorrow", inbox.NewTask.Day?.Label);
        Assert.Equal("Errands", inbox.NewTask.Area?.Id);
        Assert.True(inbox.NewTask.TopPriority);
        inbox.NewTask.SaveCommand.Execute(null);

        var task = planner.Task("Buy shoes");
        Assert.Equal(new TimeOnly(9, 0), task.PlannedTime);
        Assert.Contains("run", planner.Tags.Names());
        Assert.Equal(string.Empty, inbox.Composer.NewTaskTitle);
    }

    [Fact]
    public async Task TodaysTypedLineStillQuickAddsAsBefore()
    {
        var today = List(ListKind.Today, day => day);
        today.Composer.NewTaskTitle = "Pay rent #home";

        Assert.True(today.Composer.ShowsSend);
        Assert.Equal("Composer.Add", today.Composer.ButtonName);
        await today.Composer.PressCommand.ExecuteAsync(null);

        Assert.Equal(Today, planner.Task("Pay rent").PlannedDate);
        Assert.False(today.NewTask.IsOpen);
    }

    [Fact]
    public void AComposerWithoutAFormOnlySends()
    {
        var box = Composer(_ => null);

        Assert.False(box.HasForm);
        Assert.False(box.ShowsPlus);
        Assert.Equal("Composer.Add", box.ButtonName);
        Assert.False(box.PressCommand.CanExecute(null));
        Assert.False(box.OpenFormCommand.CanExecute(null));
    }

    private static string Plain(ComposerChipViewModel chip) => chip.Label.StartsWith("3", StringComparison.Ordinal) ? "Wants.Money" : chip.Label;

    private WantsViewModel Wants(ChatViewModel? chat = null) =>
        new(planner.Wants, planner.Settings, planner.Strings, planner.Time, action => action(), chat);

    private HabitsViewModel Habits(ChatViewModel? chat = null) =>
        new(planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => true, action => action(), chat: chat);

    private GoalsViewModel Goals(ChatViewModel? chat = null) =>
        new(planner.Goals, planner.Tasks, planner.Settings, planner.Strings, planner.Time, () => true, action => action(), planner.Habits, chat);

    private ComposerViewModel Composer(Func<DateOnly, DateOnly?> day) => new(
        planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day, action => action());

    private ListViewModel List(ListKind kind, Func<DateOnly, DateOnly?> day) => new(
        kind, planner.Tasks, planner.Areas, Composer(day), planner.Sync, planner.Settings, planner.Strings, planner.Time, _ => null, () => true, planner.Tick, action => action());

    private sealed class FakeAssistant : IAssistantClient
    {
        public Queue<AssistantReply> Replies { get; } = new();

        public Task<AssistantReply> SendAsync(IReadOnlyList<AssistantMessage> messages, CancellationToken cancellationToken = default) =>
            Task.FromResult(Replies.Dequeue());
    }

    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged;

        public AuthSession Session { get; } = new AuthSession.SignedIn(TestPlanner.Owner, "me@example.com");

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task SignOutAsync() => Task.CompletedTask;

        public Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken) => Task.FromResult(SessionRenewal.Renewed);

        public Task EndSessionAsync()
        {
            SessionChanged?.Invoke(this, Session);
            return Task.CompletedTask;
        }
    }
}

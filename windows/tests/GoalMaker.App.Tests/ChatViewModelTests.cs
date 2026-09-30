using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Assistant;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Tests;

/// <summary>
/// The quick chat in the composer (M7) over a fake assistant: the switch and its memory, a message and
/// its answer, errors, offline and signed out, Clear, and quick-add exactly as before on quick-add.
/// </summary>
public sealed class ChatViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly FakeAssistant assistant = new();
    private readonly FakeAuth auth = new();
    private int syncs;

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheSwitchStartsOnQuickAddAndRemembersChat()
    {
        var chat = Chat();
        Assert.False(chat.IsChatChosen);
        Assert.False(Composer(chat).IsChat);

        chat.IsChatChosen = true;

        Assert.Equal(ComposerMode.Chat, planner.Settings.ComposerMode);
        var again = Chat();
        Assert.True(again.IsChatChosen);
        Assert.True(Composer(again).IsChat);
    }

    [Fact]
    public void TheShortcutFlipsTheSwitchBothWays()
    {
        var chat = Chat();
        var composer = Composer(chat);

        chat.SwitchCommand.Execute(null);
        Assert.True(composer.IsChat);
        Assert.Equal("Chat.Placeholder", composer.Placeholder);
        Assert.Equal("Chat.Send", composer.SendName);

        chat.SwitchCommand.Execute(null);
        Assert.False(composer.IsChat);
        Assert.Equal(ComposerMode.QuickAdd, planner.Settings.ComposerMode);
        Assert.Equal("Composer.Placeholder", composer.Placeholder);
        Assert.Equal("Composer.Add", composer.SendName);
    }

    [Fact]
    public async Task AMessageGoesWithTheWholeThreadAndItsAnswerShows()
    {
        var chat = Chat(chosen: true);
        var composer = Composer(chat);
        var added = 0;
        composer.Added += (_, _) => added++;
        assistant.Replies.Enqueue(new AssistantReply.Answer("Added Call the bank for tomorrow."));
        assistant.Replies.Enqueue(new AssistantReply.Answer("One task: Call the bank."));

        composer.NewTaskTitle = "add call the bank tomorrow";
        await composer.AddTaskCommand.ExecuteAsync(null);
        composer.NewTaskTitle = "what's on tomorrow?";
        await composer.AddTaskCommand.ExecuteAsync(null);

        Assert.Equal(
            [
                new AssistantMessage(AssistantRole.User, "add call the bank tomorrow"),
                new AssistantMessage(AssistantRole.Model, "Added Call the bank for tomorrow."),
                new AssistantMessage(AssistantRole.User, "what's on tomorrow?"),
            ],
            assistant.Sent[1]);
        Assert.Equal(4, chat.Lines.Count);
        Assert.True(chat.Lines[2].IsOwner);
        Assert.Equal("One task: Call the bank.", chat.Lines[3].Text);
        Assert.Equal("Chat.Line(Chat.Assistant,One task: Call the bank.)", chat.Lines[3].AutomationName);
        Assert.True(chat.ShowsThread);
        Assert.Equal(string.Empty, composer.NewTaskTitle);

        // The chat saved nothing itself, the box stays open, and a sync brings its changes in.
        Assert.Empty(planner.Tasks.All());
        Assert.Equal(0, added);
        Assert.Equal(2, syncs);
    }

    [Fact]
    public async Task ThinkingShowsWhileTheMessageIsOnItsWay()
    {
        var chat = Chat(chosen: true);
        var composer = Composer(chat);
        var answer = new TaskCompletionSource<AssistantReply>();
        assistant.Pending = answer.Task;

        composer.NewTaskTitle = "move everything from today to Friday";
        var sending = composer.AddTaskCommand.ExecuteAsync(null);

        Assert.True(chat.IsBusy);
        Assert.True(chat.ShowsThread);
        Assert.Single(chat.Lines);
        composer.NewTaskTitle = "and one more";
        Assert.False(composer.AddTaskCommand.CanExecute(null));

        answer.SetResult(new AssistantReply.Answer("Moved 3 tasks to Friday."));
        await sending;

        Assert.False(chat.IsBusy);
        Assert.Equal(2, chat.Lines.Count);
        Assert.True(composer.AddTaskCommand.CanExecute(null));
    }

    [Fact]
    public async Task AnErrorSaysWhatWentWrongAndGivesTheLineBack()
    {
        var chat = Chat(chosen: true);
        var composer = Composer(chat);
        assistant.Replies.Enqueue(new AssistantReply.Failure(AssistantProblem.RateLimited));

        composer.NewTaskTitle = "what's left for GoalMaker?";
        await composer.AddTaskCommand.ExecuteAsync(null);

        Assert.Empty(chat.Lines);
        Assert.Equal("what's left for GoalMaker?", composer.NewTaskTitle);
        Assert.Equal("Chat.RateLimited", chat.Notice);
        Assert.True(chat.IsChat);
        Assert.Equal(0, syncs);

        // Sent again, it goes alone rather than after the one that failed.
        assistant.Replies.Enqueue(new AssistantReply.Answer("Two items are left."));
        await composer.AddTaskCommand.ExecuteAsync(null);
        Assert.Single(assistant.Sent[1]);
        Assert.Equal(string.Empty, chat.Notice);
    }

    [Theory]
    [InlineData(AssistantProblem.ProviderLimit, "Chat.ProviderLimit")]
    [InlineData(AssistantProblem.BadRequest, "Chat.BadRequest")]
    [InlineData(AssistantProblem.Failed, "Chat.Failed")]
    [InlineData(AssistantProblem.SignedOut, "Chat.SessionRefused")]
    public async Task EachProblemHasItsOwnWords(AssistantProblem problem, string words)
    {
        var chat = Chat(chosen: true);
        assistant.Replies.Enqueue(new AssistantReply.Failure(problem));

        Assert.False(await chat.SendAsync("hello"));

        Assert.Equal(words, chat.Notice);
        Assert.True(chat.IsChat);
    }

    [Fact]
    public async Task NoKeyOnTheServerTurnsChatOffAndQuickAddKeepsWorking()
    {
        var chat = Chat(chosen: true);
        var composer = Composer(chat);
        assistant.Replies.Enqueue(new AssistantReply.Failure(AssistantProblem.Unavailable));

        composer.NewTaskTitle = "Call the bank";
        await composer.AddTaskCommand.ExecuteAsync(null);

        Assert.Equal(ChatAvailability.NotSetUp, chat.Availability);
        Assert.False(composer.IsChat);
        Assert.True(chat.IsChatChosen);
        Assert.Equal("Chat.NotSetUp", chat.Notice);
        Assert.Equal("Chat.NotSetUp", chat.SwitchHelp);
        Assert.Equal("Call the bank", composer.NewTaskTitle);

        await composer.AddTaskCommand.ExecuteAsync(null);
        Assert.Equal("Call the bank", planner.Task("Call the bank").Title);
        Assert.Single(assistant.Sent);

        // After a while the server is asked again.
        planner.Time.Advance(ChatViewModel.NotSetUpFor);
        auth.SignIn();
        Assert.True(composer.IsChat);
    }

    [Fact]
    public async Task OfflineTheSwitchSaysSoAndEnterAddsATask()
    {
        var chat = Chat(chosen: true);
        var composer = Composer(chat);

        // The test planner's server can't be reached, so a sync run goes offline.
        await planner.Sync.SyncNowAsync(TestContext.Current.CancellationToken);

        Assert.Equal(ChatAvailability.Offline, chat.Availability);
        Assert.Equal("Chat.Offline", chat.Notice);
        Assert.True(chat.CanSwitch);
        composer.NewTaskTitle = "Buy stamps";
        await composer.AddTaskCommand.ExecuteAsync(null);
        Assert.Equal("Buy stamps", planner.Task("Buy stamps").Title);
        Assert.Empty(assistant.Sent);
    }

    [Fact]
    public async Task AMessageThatCouldNotGoMarksChatOffline()
    {
        var chat = Chat(chosen: true);
        assistant.Replies.Enqueue(new AssistantReply.Failure(AssistantProblem.Offline));

        Assert.False(await chat.SendAsync("hello"));

        Assert.Equal(ChatAvailability.Offline, chat.Availability);
        Assert.False(chat.IsChat);
        Assert.Equal(1, syncs);
    }

    [Fact]
    public void SignedOutChatCannotBeSwitchedOn()
    {
        auth.SignOut();
        var chat = Chat();

        Assert.Equal(ChatAvailability.SignedOut, chat.Availability);
        Assert.False(chat.CanSwitch);
        Assert.False(chat.SwitchCommand.CanExecute(null));
        Assert.Equal("Chat.SignedOut", chat.SwitchHelp);

        chat.IsChatChosen = true;

        Assert.False(chat.IsChatChosen);
        Assert.Equal(ComposerMode.QuickAdd, planner.Settings.ComposerMode);
    }

    [Fact]
    public void SigningOutKeepsTheChoiceForLater()
    {
        var chat = Chat(chosen: true);

        auth.SignOut();
        Assert.False(chat.IsChat);
        Assert.Equal("Chat.SignedOut", chat.Notice);

        auth.SignIn();
        Assert.True(chat.IsChat);
        Assert.Equal(string.Empty, chat.Notice);
    }

    [Fact]
    public void ADevelopmentBuildWithoutAServerHasNoChat()
    {
        var chat = Chat(localOnly: true);

        Assert.Equal(ChatAvailability.LocalOnly, chat.Availability);
        Assert.False(chat.CanSwitch);
    }

    [Fact]
    public async Task ClearEmptiesTheThreadAndDropsAnAnswerOnItsWay()
    {
        var chat = Chat(chosen: true);
        assistant.Replies.Enqueue(new AssistantReply.Answer("Hi."));
        await chat.SendAsync("hello");
        var answer = new TaskCompletionSource<AssistantReply>();
        assistant.Pending = answer.Task;
        var sending = chat.SendAsync("and today?");

        chat.ClearCommand.Execute(null);
        answer.SetResult(new AssistantReply.Answer("Too late."));
        await sending;

        Assert.Empty(chat.Lines);
        Assert.False(chat.IsBusy);
        Assert.False(chat.ShowsThread);
        Assert.False(chat.ClearCommand.CanExecute(null));
    }

    [Fact]
    public async Task QuickAddIsUnchangedWithTheSwitchOnQuickAdd()
    {
        var chat = Chat();
        var composer = Composer(chat);
        var added = 0;
        composer.Added += (_, _) => added++;

        composer.NewTaskTitle = "Call the bank tomorrow 17:00 #money";
        Assert.True(composer.HasChips);
        await composer.AddTaskCommand.ExecuteAsync(null);

        var task = planner.Task("Call the bank");
        Assert.Equal(new DateOnly(2026, 9, 19), task.PlannedDate);
        Assert.Equal(new TimeOnly(17, 0), task.PlannedTime);
        Assert.Equal(1, added);
        Assert.Empty(assistant.Sent);
        Assert.False(chat.ShowsThread);
    }

    [Fact]
    public void AChatMessageShowsNoTaskPreview()
    {
        var composer = Composer(Chat(chosen: true));

        composer.NewTaskTitle = "Call the bank tomorrow 17:00 #money";

        Assert.False(composer.HasChips);
        Assert.True(composer.AddTaskCommand.CanExecute(null));
    }

    private ChatViewModel Chat(bool chosen = false, bool localOnly = false)
    {
        if (chosen)
        {
            planner.Settings.ComposerMode = ComposerMode.Chat;
        }

        return new ChatViewModel(
            assistant, auth, planner.Sync, planner.Settings, planner.Strings, planner.Time, action => action(), () => syncs++, localOnly);
    }

    private ComposerViewModel Composer(ChatViewModel chat) => new(
        planner.Tasks,
        planner.Areas,
        planner.Tags,
        planner.Projects,
        planner.Settings,
        planner.Strings,
        planner.Time,
        _ => null,
        _ => null,
        action => action(),
        chat: chat);

    /// <summary>Answers from a queue, or waits on <see cref="Pending"/>, and keeps every thread it was sent.</summary>
    private sealed class FakeAssistant : IAssistantClient
    {
        public Queue<AssistantReply> Replies { get; } = new();

        public List<IReadOnlyList<AssistantMessage>> Sent { get; } = [];

        public Task<AssistantReply>? Pending { get; set; }

        public Task<AssistantReply> SendAsync(IReadOnlyList<AssistantMessage> messages, CancellationToken cancellationToken = default)
        {
            Sent.Add(messages);
            if (Pending is { } pending)
            {
                Pending = null;
                return pending;
            }

            return Task.FromResult(Replies.Dequeue());
        }
    }

    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged;

        public AuthSession Session { get; private set; } = new AuthSession.SignedIn(TestPlanner.Owner, "me@example.com");

        public void SignIn() => Publish(new AuthSession.SignedIn(TestPlanner.Owner, "me@example.com"));

        public void SignOut() => Publish(new AuthSession.SignedOut());

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task SignOutAsync()
        {
            SignOut();
            return Task.CompletedTask;
        }

        public Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken) => Task.FromResult(SessionRenewal.Renewed);

        public Task EndSessionAsync()
        {
            Publish(new AuthSession.SignedOut(SessionEnded: true));
            return Task.CompletedTask;
        }

        private void Publish(AuthSession session)
        {
            Session = session;
            SessionChanged?.Invoke(this, session);
        }
    }
}

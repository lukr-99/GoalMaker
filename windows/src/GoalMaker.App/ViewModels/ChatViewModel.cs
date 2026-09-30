using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Assistant;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The quick chat behind every composer (spec, "Quick chat (M7)"): the switch between quick-add and
/// chat, remembered on this PC, and the one thread all composers share. The thread lives only here, in
/// memory, and the whole of it goes with each message. Signed out, offline, or with chat not set up on
/// the server, the switch says why and Enter adds a task as usual; the owner's choice is kept for when
/// chat works again. After an answer a sync runs, so what the chat changed shows in the lists at once.
/// </summary>
public sealed partial class ChatViewModel : ObservableObject
{
    /// <summary>How long a server that said it has no model key is taken at its word before chat is tried again.</summary>
    public static readonly TimeSpan NotSetUpFor = TimeSpan.FromMinutes(10);

    private static readonly string[] Derived =
    [
        nameof(IsChatChosen), nameof(IsAvailable), nameof(IsChat), nameof(CanSwitch), nameof(SwitchHelp),
        nameof(Notice), nameof(HasNotice), nameof(HasLines), nameof(ShowsThread), nameof(CanClear),
    ];

    private readonly IAssistantClient assistant;
    private readonly IAuthGateway auth;
    private readonly SyncCoordinator sync;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action requestSync;
    private readonly bool localOnly;
    private DateTimeOffset? notSetUpUntil;
    private bool unreachable;
    private int turn;
    private CancellationTokenSource? call;

    [ObservableProperty]
    private ChatAvailability availability;

    [ObservableProperty]
    private bool isBusy;

    [ObservableProperty]
    private string problem = string.Empty;

    /// <param name="requestSync">Runs a sync after an answer, so the chat's changes reach the replica.</param>
    /// <param name="localOnly">A development build with no server: chat can't work at all.</param>
    public ChatViewModel(
        IAssistantClient assistant,
        IAuthGateway auth,
        SyncCoordinator sync,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Action<Action> runOnUi,
        Action requestSync,
        bool localOnly = false)
    {
        this.assistant = assistant;
        this.auth = auth;
        this.sync = sync;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.requestSync = requestSync;
        this.localOnly = localOnly;
        auth.SessionChanged += (_, _) => runOnUi(Refresh);
        sync.StatusChanged += (_, status) => runOnUi(() =>
        {
            // A run the server answered means it can be reached again.
            if (status.State is SyncState.Idle or SyncState.NeedsAttention)
            {
                unreachable = false;
            }

            Refresh();
        });
        availability = Compute();
    }

    /// <summary>The thread so far, oldest first.</summary>
    public ObservableCollection<ChatLineViewModel> Lines { get; } = [];

    /// <summary>The owner's choice on the switch, kept on this PC; it holds even while chat can't be used.</summary>
    public bool IsChatChosen
    {
        get => settings.ComposerMode == ComposerMode.Chat;
        set
        {
            var mode = value ? ComposerMode.Chat : ComposerMode.QuickAdd;
            if (mode == settings.ComposerMode || (value && !IsAvailable))
            {
                // A two-way binding that was refused still has to show the real state.
                OnPropertyChanged(nameof(IsChatChosen));
                return;
            }

            settings.ComposerMode = mode;
            Problem = string.Empty;
            Refresh();
        }
    }

    public bool IsAvailable => Availability == ChatAvailability.Available;

    /// <summary>Whether a line goes to the chat now: chosen and available.</summary>
    public bool IsChat => IsChatChosen && IsAvailable;

    /// <summary>Chat can always be switched off, and switched on only when it works.</summary>
    public bool CanSwitch => IsAvailable || IsChatChosen;

    /// <summary>The switch's tooltip and help text: what it does, or why chat can't be used now.</summary>
    public string SwitchHelp => IsAvailable ? strings.Get("Chat.SwitchHelp") : Reason;

    /// <summary>The line above the composer: why chat is off although chosen, or what went wrong with the last message.</summary>
    public string Notice => IsChatChosen && !IsAvailable ? Reason : IsChat ? Problem : string.Empty;

    public bool HasNotice => Notice.Length > 0;

    public bool HasLines => Lines.Count > 0;

    /// <summary>The thread shows above the composer while chatting, once there is something in it.</summary>
    public bool ShowsThread => IsChat && (HasLines || IsBusy);

    public bool CanClear => HasLines || IsBusy || Problem.Length > 0;

    private string Reason => Availability switch
    {
        ChatAvailability.SignedOut => strings.Get("Chat.SignedOut"),
        ChatAvailability.Offline => strings.Get("Chat.Offline"),
        ChatAvailability.NotSetUp => strings.Get("Chat.NotSetUp"),
        ChatAvailability.LocalOnly => strings.Get("Chat.LocalOnly"),
        _ => string.Empty,
    };

    /// <summary>Whether <paramref name="text"/> can go to the chat now.</summary>
    public bool CanSend(string text) => IsChat && !IsBusy && text.Trim().Length > 0;

    /// <summary>
    /// Sends <paramref name="text"/> with the thread before it. True when it was answered, or dropped
    /// by Clear; false when it didn't go, so the composer can give the line back.
    /// </summary>
    public async Task<bool> SendAsync(string text)
    {
        Refresh();
        var line = text.Trim();
        if (!CanSend(line))
        {
            return false;
        }

        Problem = string.Empty;
        var mine = new ChatLineViewModel(AssistantRole.User, line, strings);
        Lines.Add(mine);
        IsBusy = true;
        var sent = ++turn;
        call = new CancellationTokenSource();
        Refresh();

        AssistantReply reply;
        try
        {
            reply = await assistant.SendAsync([.. Lines.Select(shown => shown.ToMessage())], call.Token);
        }
        catch (OperationCanceledException)
        {
            return true;
        }

        if (sent != turn)
        {
            // Cleared while it ran: the answer belongs to a thread that is gone.
            return true;
        }

        IsBusy = false;
        call.Dispose();
        call = null;
        if (reply is AssistantReply.Answer answer)
        {
            Lines.Add(new ChatLineViewModel(AssistantRole.Model, answer.Text, strings));
            requestSync();
            Refresh();
            return true;
        }

        var failure = (AssistantReply.Failure)reply;
        Lines.Remove(mine);
        Problem = ProblemText(failure.Problem);
        if (failure.Problem == AssistantProblem.Unavailable)
        {
            notSetUpUntil = time.GetUtcNow() + NotSetUpFor;
        }
        else if (failure.Problem == AssistantProblem.Offline)
        {
            // Sync finds out when the server is back and says so.
            unreachable = true;
            requestSync();
        }

        Refresh();
        return false;
    }

    /// <summary>The switch, from its button or Ctrl+Shift+Space.</summary>
    [RelayCommand(CanExecute = nameof(CanSwitch))]
    private void Switch() => IsChatChosen = !IsChatChosen;

    /// <summary>Empties the thread, dropping a message still on its way.</summary>
    [RelayCommand(CanExecute = nameof(CanClear))]
    private void Clear()
    {
        turn++;
        call?.Cancel();
        call?.Dispose();
        call = null;
        IsBusy = false;
        Lines.Clear();
        Problem = string.Empty;
        Refresh();
    }

    partial void OnProblemChanged(string value) => Refresh();

    private string ProblemText(AssistantProblem failure) => failure switch
    {
        AssistantProblem.Unavailable => strings.Get("Chat.NotSetUp"),
        AssistantProblem.RateLimited => strings.Get("Chat.RateLimited"),
        AssistantProblem.ProviderLimit => strings.Get("Chat.ProviderLimit"),
        AssistantProblem.BadRequest => strings.Get("Chat.BadRequest"),
        AssistantProblem.Offline => strings.Get("Chat.Offline"),
        AssistantProblem.SignedOut => strings.Get("Chat.SessionRefused"),
        _ => strings.Get("Chat.Failed"),
    };

    private ChatAvailability Compute()
    {
        if (localOnly)
        {
            return ChatAvailability.LocalOnly;
        }

        if (auth.Session is not AuthSession.SignedIn)
        {
            return ChatAvailability.SignedOut;
        }

        if (unreachable || sync.Status.State == SyncState.Offline)
        {
            return ChatAvailability.Offline;
        }

        return notSetUpUntil is { } until && time.GetUtcNow() < until ? ChatAvailability.NotSetUp : ChatAvailability.Available;
    }

    private void Refresh()
    {
        Availability = Compute();
        foreach (var name in Derived)
        {
            OnPropertyChanged(name);
        }

        SwitchCommand.NotifyCanExecuteChanged();
        ClearCommand.NotifyCanExecuteChanged();
    }
}

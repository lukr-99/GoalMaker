using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.App.Localization;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Notices;

namespace GoalMaker.App.ViewModels;

/// <summary>The main window: a loading state, sign-in, or the signed-in navigation.</summary>
public sealed partial class ShellViewModel : ObservableObject
{
    [ObservableProperty]
    private bool isLoading = true;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsSignedOut))]
    private bool isSignedIn;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSettingsMark))]
    private bool hasProblems;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSettingsMark))]
    private bool hasUpdate;

    [ObservableProperty]
    private string settingsMarkText = string.Empty;

    public ShellViewModel(IAuthGateway auth, SignInViewModel signIn, NoticeBoard notices, IStrings strings, Action<Action> runOnUi)
    {
        SignIn = signIn;
        auth.SessionChanged += (_, session) => runOnUi(() => Apply(session));
        // The icons on the Settings item (docs/problems.md): a warning for a problem nobody has read, a
        // download arrow for an update the last check found, and a tooltip that says which.
        notices.Changed += (_, _) => runOnUi(() => ShowMarks(notices.Marked, strings));
        ShowMarks(notices.Marked, strings);
        Apply(auth.Session);
    }

    public SignInViewModel SignIn { get; }

    /// <summary>Whether the Settings item wears a mark at all; a problem and an update sit side by side.</summary>
    public bool HasSettingsMark => HasProblems || HasUpdate;

    public bool IsSignedOut => !IsLoading && !IsSignedIn;

    partial void OnIsLoadingChanged(bool value) => OnPropertyChanged(nameof(IsSignedOut));

    private void ShowMarks(IReadOnlyList<Notice> marked, IStrings strings)
    {
        HasProblems = marked.Any(notice => notice is Notice.ProblemWaiting);
        HasUpdate = marked.Any(notice => notice is Notice.UpdateReady);
        SettingsMarkText = NoticeCopy.Mark(marked, strings);
    }

    private void Apply(AuthSession session)
    {
        IsLoading = session is AuthSession.Loading;
        IsSignedIn = session is AuthSession.SignedIn;
        SignIn.Apply(session);
    }
}

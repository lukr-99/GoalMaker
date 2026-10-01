using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Problems;
using GoalMaker.Core.Updates;

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

    public ShellViewModel(IAuthGateway auth, SignInViewModel signIn, ProblemLog problems, UpdateService updates, Action<Action> runOnUi)
    {
        SignIn = signIn;
        auth.SessionChanged += (_, session) => runOnUi(() => Apply(session));
        // The quiet mark on the Settings item: something went wrong while nobody was watching.
        problems.Changed += (_, _) => runOnUi(() => HasProblems = problems.IsMarked);
        HasProblems = problems.IsMarked;
        // The accent mark on the Settings item: the last check found an update that is not installed.
        updates.WaitingChanged += (_, _) => runOnUi(() => HasUpdate = updates.Waiting is not null);
        HasUpdate = updates.Waiting is not null;
        Apply(auth.Session);
    }

    public SignInViewModel SignIn { get; }

    /// <summary>Whether the Settings item wears a mark at all; an update shows over a problem.</summary>
    public bool HasSettingsMark => HasProblems || HasUpdate;

    public bool IsSignedOut => !IsLoading && !IsSignedIn;

    partial void OnIsLoadingChanged(bool value) => OnPropertyChanged(nameof(IsSignedOut));

    private void Apply(AuthSession session)
    {
        IsLoading = session is AuthSession.Loading;
        IsSignedIn = session is AuthSession.SignedIn;
        SignIn.Apply(session);
    }
}

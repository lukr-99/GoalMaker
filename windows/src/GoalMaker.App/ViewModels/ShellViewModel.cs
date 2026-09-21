using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.Core.Auth;
using GoalMaker.Core.Problems;

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
    private bool hasProblems;

    public ShellViewModel(IAuthGateway auth, SignInViewModel signIn, ProblemLog problems, Action<Action> runOnUi)
    {
        SignIn = signIn;
        auth.SessionChanged += (_, session) => runOnUi(() => Apply(session));
        // The quiet mark on the Settings item: something went wrong while nobody was watching.
        problems.Changed += (_, _) => runOnUi(() => HasProblems = problems.IsMarked);
        HasProblems = problems.IsMarked;
        Apply(auth.Session);
    }

    public SignInViewModel SignIn { get; }

    public bool IsSignedOut => !IsLoading && !IsSignedIn;

    partial void OnIsLoadingChanged(bool value) => OnPropertyChanged(nameof(IsSignedOut));

    private void Apply(AuthSession session)
    {
        IsLoading = session is AuthSession.Loading;
        IsSignedIn = session is AuthSession.SignedIn;
    }
}

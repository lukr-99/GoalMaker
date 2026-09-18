using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.Core.Auth;

namespace GoalMaker.App.ViewModels;

/// <summary>The main window: a loading state, sign-in, or the signed-in navigation.</summary>
public sealed partial class ShellViewModel : ObservableObject
{
    [ObservableProperty]
    private bool isLoading = true;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsSignedOut))]
    private bool isSignedIn;

    public ShellViewModel(IAuthGateway auth, SignInViewModel signIn, Action<Action> runOnUi)
    {
        SignIn = signIn;
        auth.SessionChanged += (_, session) => runOnUi(() => Apply(session));
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

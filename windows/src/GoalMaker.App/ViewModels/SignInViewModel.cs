using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;

namespace GoalMaker.App.ViewModels;

/// <summary>Drives the two-step email code sign-in. Success is observed through the auth session.</summary>
public sealed partial class SignInViewModel : ObservableObject
{
    private readonly IAuthGateway auth;
    private readonly IStrings strings;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsEmailStep))]
    private bool isCodeStep;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CodeSentText))]
    private string email = string.Empty;

    [ObservableProperty]
    private string code = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsIdle))]
    private bool isBusy;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasError))]
    private string errorText = string.Empty;

    public SignInViewModel(IAuthGateway auth, IStrings strings, string? devBackend)
    {
        this.auth = auth;
        this.strings = strings;
        DevBackendText = devBackend is null ? null : strings.Get("SignIn.DevBackend", devBackend);
    }

    public bool IsEmailStep => !IsCodeStep;

    public bool IsIdle => !IsBusy;

    public bool HasError => ErrorText.Length > 0;

    public string CodeSentText => strings.Get("SignIn.CodeSent", Email.Trim());

    public string? DevBackendText { get; }

    [RelayCommand]
    private async Task SendCodeAsync()
    {
        var address = EmailAddress.Parse(Email);
        if (address is null)
        {
            ErrorText = strings.Get("SignIn.Error.InvalidEmail");
            return;
        }

        if (await RunAsync(() => auth.SendCodeAsync(address, CancellationToken.None)))
        {
            Code = string.Empty;
            IsCodeStep = true;
        }
    }

    [RelayCommand]
    private async Task VerifyCodeAsync()
    {
        if (IsBusy)
        {
            return;
        }

        var address = EmailAddress.Parse(Email);
        if (address is null)
        {
            IsCodeStep = false;
            ErrorText = strings.Get("SignIn.Error.InvalidEmail");
            return;
        }

        var parsed = SignInCode.Parse(Code);
        if (parsed is null)
        {
            ErrorText = strings.Get("SignIn.Error.InvalidCode");
            return;
        }

        await RunAsync(() => auth.VerifyCodeAsync(address, parsed, CancellationToken.None));
    }

    [RelayCommand]
    private void UseAnotherEmail()
    {
        IsCodeStep = false;
        Code = string.Empty;
        ErrorText = string.Empty;
    }

    partial void OnEmailChanged(string value) => ErrorText = string.Empty;

    partial void OnCodeChanged(string value)
    {
        var digits = new string([.. value.Where(char.IsAsciiDigit).Take(SignInCode.Length)]);
        if (digits != value)
        {
            Code = digits;
            return;
        }

        ErrorText = string.Empty;
        if (digits.Length == SignInCode.Length && !IsBusy)
        {
            _ = VerifyCodeCommand.ExecuteAsync(null);
        }
    }

    private async Task<bool> RunAsync(Func<Task<AuthResult>> action)
    {
        IsBusy = true;
        ErrorText = string.Empty;
        try
        {
            var result = await action();
            switch (result)
            {
                case AuthResult.Success:
                    return true;
                case AuthResult.WrongOrExpiredCode:
                    Code = string.Empty;
                    ErrorText = strings.Get("SignIn.Error.WrongCode");
                    break;
                case AuthResult.TooManyRequests:
                    ErrorText = strings.Get("SignIn.Error.TooManyRequests");
                    break;
                case AuthResult.Offline:
                    ErrorText = strings.Get("SignIn.Error.Offline");
                    break;
                case AuthResult.Failed failed:
                    ErrorText = strings.Get("SignIn.Error.Other", failed.Detail);
                    break;
            }

            return false;
        }
        finally
        {
            IsBusy = false;
        }
    }
}

using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Drives the two-step email code sign-in. Success is observed through the auth session. A dev build
/// against the local stack gets two ways past the post: the dev account, which the stack lets in with
/// a code it never sends, and reading the code for any other address out of the stack's own mailbox
/// (docs/sign-in.md).
/// </summary>
public sealed partial class SignInViewModel : ObservableObject
{
    private readonly IAuthGateway auth;
    private readonly IStrings strings;
    private readonly Func<string, CancellationToken, Task<string?>>? devCode;

    // The mail lands within a second or so on a local stack; after this the owner types it.
    private static readonly TimeSpan Wait = TimeSpan.FromMilliseconds(600);
    private const int Attempts = 6;

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

    public SignInViewModel(
        IAuthGateway auth,
        IStrings strings,
        string? devBackend,
        Func<string, CancellationToken, Task<string?>>? devCode = null)
    {
        this.auth = auth;
        this.strings = strings;
        this.devCode = devCode;
        DevBackendText = devBackend is null ? null : strings.Get("SignIn.DevBackend", devBackend);
    }

    public bool IsEmailStep => !IsCodeStep;

    public bool IsIdle => !IsBusy;

    public bool HasError => ErrorText.Length > 0;

    public string CodeSentText => strings.Get("SignIn.CodeSent", Email.Trim());

    public string? DevBackendText { get; }

    /// <summary>This build is a dev one on the local stack, which is where both dev doors are.</summary>
    public bool HasDevSignIn => devCode is not null;

    /// <summary>"Sign in as dev@goalmaker.test", so the address is never a surprise.</summary>
    public string DevAccountText => strings.Get("SignIn.DevAccount", DevSignIn.Email);

    /// <summary>
    /// The dev account: the local stack takes its code without sending anything, so one press signs
    /// in. It is its own account with its own data, kept apart from the one the owner signs in as.
    /// </summary>
    [RelayCommand]
    private async Task SignInAsDevAsync()
    {
        if (!HasDevSignIn || IsBusy || EmailAddress.Parse(DevSignIn.Email) is not { } address)
        {
            return;
        }

        Email = DevSignIn.Email;
        if (await RunAsync(() => auth.SendCodeAsync(address, CancellationToken.None)))
        {
            IsCodeStep = true;
            // A full code verifies itself, so this is the whole sign-in.
            Code = DevSignIn.Code;
        }
    }

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
            await FillCodeAsync();
        }
    }

    /// <summary>
    /// Reads the code out of the local stack's mailbox and fills it in, which signs in, since a full
    /// code verifies itself. The mail takes a moment to land, so it asks a few times before giving up
    /// and leaving the owner to type it.
    /// </summary>
    [RelayCommand]
    private async Task FillCodeAsync()
    {
        if (devCode is null || IsBusy)
        {
            return;
        }

        var address = Email.Trim();
        for (var attempt = 0; attempt < Attempts && IsCodeStep && Code.Length == 0; attempt++)
        {
            if (attempt > 0)
            {
                await Task.Delay(Wait).ConfigureAwait(true);
            }

            if (await devCode(address, CancellationToken.None).ConfigureAwait(true) is { } code)
            {
                Code = code;
                return;
            }
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

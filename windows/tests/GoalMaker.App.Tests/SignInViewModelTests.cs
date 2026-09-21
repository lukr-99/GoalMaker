using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;

namespace GoalMaker.App.Tests;

public sealed class SignInViewModelTests
{
    private readonly FakeAuth auth = new();

    private SignInViewModel Create() => new(auth, new KeyStrings(), devBackend: null);

    [Fact]
    public async Task AnInvalidEmailIsCaughtBeforeAnythingIsSent()
    {
        var viewModel = Create();
        viewModel.Email = "nope";
        await viewModel.SendCodeCommand.ExecuteAsync(null);
        Assert.Equal("SignIn.Error.InvalidEmail", viewModel.ErrorText);
        Assert.Empty(auth.Sent);
    }

    [Fact]
    public async Task SendingACodeMovesToTheCodeStep()
    {
        var viewModel = Create();
        viewModel.Email = " me@example.com ";
        await viewModel.SendCodeCommand.ExecuteAsync(null);
        Assert.Equal(["me@example.com"], auth.Sent);
        Assert.True(viewModel.IsCodeStep);
        Assert.False(viewModel.IsBusy);
    }

    [Fact]
    public async Task TheSixthDigitSubmitsTheCode()
    {
        var viewModel = Create();
        viewModel.Email = "me@example.com";
        await viewModel.SendCodeCommand.ExecuteAsync(null);
        viewModel.Code = "123 45";
        Assert.Empty(auth.Verified);
        viewModel.Code = "1234567";
        Assert.Equal("123456", viewModel.Code);
        Assert.Equal([("me@example.com", "123456")], auth.Verified);
    }

    [Fact]
    public async Task AWrongCodeClearsTheFieldAndExplains()
    {
        auth.VerifyResult = new AuthResult.WrongOrExpiredCode();
        var viewModel = Create();
        viewModel.Email = "me@example.com";
        await viewModel.SendCodeCommand.ExecuteAsync(null);
        viewModel.Code = "000000";
        Assert.Equal("SignIn.Error.WrongCode", viewModel.ErrorText);
        Assert.Equal(string.Empty, viewModel.Code);
    }

    [Fact]
    public async Task OfflineIsReportedPlainly()
    {
        auth.SendResult = new AuthResult.Offline();
        var viewModel = Create();
        viewModel.Email = "me@example.com";
        await viewModel.SendCodeCommand.ExecuteAsync(null);
        Assert.Equal("SignIn.Error.Offline", viewModel.ErrorText);
        Assert.False(viewModel.IsCodeStep);
    }

    [Fact]
    public async Task OnePressSignsInAsTheDevAccount()
    {
        var viewModel = new SignInViewModel(
            auth,
            new KeyStrings(),
            devBackend: "http://127.0.0.1:55321",
            devCode: (email, _) => Task.FromResult<string?>(email == DevSignIn.Email ? "112233" : null));
        Assert.True(viewModel.HasDevSignIn);

        await viewModel.SignInAsDevCommand.ExecuteAsync(null);

        Assert.Equal(DevSignIn.Email, viewModel.Email);
        Assert.Equal([DevSignIn.Email], auth.Sent);
        Assert.Equal([(DevSignIn.Email, "112233")], auth.Verified);
    }

    [Fact]
    public async Task ADevBuildFillsTheCodeItReadsInTheStacksMailbox()
    {
        var viewModel = new SignInViewModel(
            auth,
            new KeyStrings(),
            devBackend: "http://127.0.0.1:55321",
            devCode: (email, _) => Task.FromResult<string?>(email == "me@example.com" ? "654321" : null));

        viewModel.Email = "me@example.com";
        await viewModel.SendCodeCommand.ExecuteAsync(null);

        Assert.Equal([("me@example.com", "654321")], auth.Verified);
    }

    [Fact]
    public void AReleaseBuildHasNeitherDoor()
    {
        Assert.False(Create().HasDevSignIn);
    }

    private sealed class KeyStrings : IStrings
    {
        public string Get(string key, params object[] arguments) => key;
    }

    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged
        {
            add { }
            remove { }
        }

        public AuthSession Session => new AuthSession.SignedOut();

        public AuthResult SendResult { get; set; } = new AuthResult.Success();

        public AuthResult VerifyResult { get; set; } = new AuthResult.Success();

        public List<string> Sent { get; } = [];

        public List<(string Email, string Code)> Verified { get; } = [];

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken)
        {
            Sent.Add(email.Value);
            return Task.FromResult(SendResult);
        }

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken)
        {
            Verified.Add((email.Value, code.Value));
            return Task.FromResult(VerifyResult);
        }

        public Task SignOutAsync() => Task.CompletedTask;
    }
}

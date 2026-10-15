using GoalMaker.App.Controls;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;

namespace GoalMaker.App.Tests;

/// <summary>The QR code on the sign-in screen (ADR 0020), over a scripted server and a clock that moves per ask.</summary>
public sealed class PhoneSignInViewModelTests
{
    private const string Scan = "Qx3mT9vB2kLw8RfN1pZc7YhJ4sDg6EaU0oKiVt5nM-_";
    private static readonly DateTimeOffset Start = new(2026, 10, 15, 9, 0, 0, TimeSpan.Zero);
    private readonly FakePcSignIn server = new();
    private readonly FakeAuth auth = new();
    private readonly TestPlanner.FakeSettings settings = new();
    private DateTimeOffset now = Start;

    private PhoneSignInViewModel Create() => new(
        server,
        auth,
        new SignInWatch(auth, settings, () => now),
        new KeyStrings(),
        "DESKTOP-1",
        () => now,
        (wait, cancellationToken) =>
        {
            cancellationToken.ThrowIfCancellationRequested();
            now += wait;
            return Task.CompletedTask;
        });

    [Fact]
    public async Task TheCodeShowsTheScanSecretAndTheNameGoesToTheServer()
    {
        server.States.Enqueue(new PcSignInState.Waiting());
        server.Hold = new TaskCompletionSource();
        var viewModel = Create();

        viewModel.Start();

        Assert.Equal("goalmaker://sign-in/pc/" + Scan, viewModel.QrText);
        Assert.True(viewModel.HasCode);
        Assert.Equal("SignIn.Phone.Waiting 2", viewModel.StatusText);
        Assert.Equal(["DESKTOP-1"], server.Devices);
        viewModel.Stop();
        server.Hold.SetResult();
        await viewModel.Running;
        Assert.False(viewModel.HasCode);
    }

    [Fact]
    public async Task AnApprovalSignsInWithItsCodeAndStartsTheWeek()
    {
        server.States.Enqueue(new PcSignInState.Waiting());
        server.States.Enqueue(new PcSignInState.Offline());
        server.States.Enqueue(Approved());
        var viewModel = Create();

        viewModel.Start();
        await viewModel.Running;

        Assert.Equal([("me@example.com", "482913")], auth.Verified);
        Assert.Equal(Start + (3 * PhoneSignInViewModel.PollEvery), settings.SignedInAt);
        Assert.False(viewModel.HasCode);
        Assert.Equal(3, server.PickUps);
    }

    [Fact]
    public async Task ACodeThatDoesNotWorkSaysSoAndOffersANewOne()
    {
        server.States.Enqueue(Approved());
        auth.VerifyResult = new AuthResult.WrongOrExpiredCode();
        var viewModel = Create();

        viewModel.Start();
        await viewModel.Running;

        Assert.Equal("SignIn.Phone.Failed", viewModel.StatusText);
        Assert.True(viewModel.CanMakeNewCode);
        Assert.Null(settings.SignedInAt);
    }

    [Theory]
    [InlineData("declined", "SignIn.Phone.Declined")]
    [InlineData("expired", "SignIn.Phone.Expired")]
    [InlineData("failed", "SignIn.Phone.Failed")]
    public async Task AnEndHidesTheCodeAndOffersANewOne(string end, string status)
    {
        server.States.Enqueue(new PcSignInState.Waiting());
        server.States.Enqueue(end switch
        {
            "declined" => new PcSignInState.Declined(),
            "expired" => new PcSignInState.Expired(),
            _ => new PcSignInState.Failed(),
        });
        var viewModel = Create();

        viewModel.Start();
        await viewModel.Running;

        Assert.False(viewModel.HasCode);
        Assert.Equal(status, viewModel.StatusText);
        Assert.True(viewModel.CanMakeNewCode);
        Assert.Empty(auth.Verified);
    }

    [Fact]
    public async Task WaitingPastTheLifetimeEndsWithoutAskingForever()
    {
        // The server keeps saying waiting; the PC stops on its own at the server's two minutes.
        server.WaitForever = true;
        var viewModel = Create();

        viewModel.Start();
        await viewModel.Running;

        Assert.Equal("SignIn.Phone.Expired", viewModel.StatusText);
        Assert.Equal(60, server.PickUps);
    }

    [Fact]
    public async Task ANewCodeIsAskedForOnlyWhenTheOwnerAsks()
    {
        server.States.Enqueue(new PcSignInState.Expired());
        var viewModel = Create();
        viewModel.Start();
        await viewModel.Running;
        Assert.Single(server.Devices);

        viewModel.Start();
        Assert.Single(server.Devices);

        server.States.Enqueue(new PcSignInState.Expired());
        viewModel.MakeNewCodeCommand.Execute(null);
        await viewModel.Running;
        Assert.Equal(2, server.Devices.Count);
    }

    [Theory]
    [InlineData("unavailable", "SignIn.Phone.Unavailable", false)]
    [InlineData("busy", "SignIn.Phone.Busy", true)]
    [InlineData("offline", "SignIn.Phone.Offline", true)]
    public async Task NoRequestSaysWhy(string why, string status, bool canRetry)
    {
        server.StartResult = why switch
        {
            "unavailable" => new PcSignInStart.Unavailable(),
            "busy" => new PcSignInStart.Busy(),
            _ => new PcSignInStart.Offline(),
        };
        var viewModel = Create();

        viewModel.Start();
        await viewModel.Running;

        Assert.False(viewModel.HasCode);
        Assert.Equal(status, viewModel.StatusText);
        Assert.Equal(canRetry, viewModel.CanMakeNewCode);
        Assert.Equal(0, server.PickUps);
    }

    [Fact]
    public void TheQrCodeIsASquareWithItsFinderPatternsAndQuietZone()
    {
        var modules = QrCode.Modules("goalmaker://sign-in/pc/" + Scan);
        var size = modules.GetLength(0);

        Assert.Equal(size, modules.GetLength(1));
        // Four squares of quiet zone, then the top left finder's dark edge.
        Assert.False(modules[3, 3]);
        Assert.True(modules[4, 4]);
        Assert.True(modules[4, 10]);
        Assert.False(modules[5, 5]);
        Assert.True(modules[6, 6]);
        Assert.True(modules[4, size - 5]);
        Assert.True(modules[size - 5, 4]);
    }

    private static PcSignInState.Approved Approved() =>
        new(EmailAddress.Parse("me@example.com")!, SignInCode.Parse("482913")!);

    private sealed class FakePcSignIn : IPcSignIn
    {
        public Queue<PcSignInState> States { get; } = new();

        public PcSignInStart StartResult { get; set; } = new PcSignInStart.Started("id-1", Scan, "pickup", TimeSpan.FromMinutes(2));

        public List<string> Devices { get; } = [];

        public int PickUps { get; private set; }

        public bool WaitForever { get; set; }

        public TaskCompletionSource? Hold { get; set; }

        public Task<PcSignInStart> StartAsync(string device, CancellationToken cancellationToken)
        {
            Devices.Add(device);
            return Task.FromResult(StartResult);
        }

        public async Task<PcSignInState> PickUpAsync(PcSignInStart.Started request, CancellationToken cancellationToken)
        {
            PickUps++;
            if (Hold is not null)
            {
                await Hold.Task;
            }

            return WaitForever ? new PcSignInState.Waiting() : States.Dequeue();
        }
    }

    private sealed class KeyStrings : IStrings
    {
        public string Get(string key, params object[] arguments) =>
            arguments.Length == 0 ? key : $"{key} {string.Join(' ', arguments)}";
    }

    private sealed class FakeAuth : IAuthGateway
    {
        public event EventHandler<AuthSession>? SessionChanged
        {
            add { }
            remove { }
        }

        public AuthSession Session => new AuthSession.SignedOut();

        public AuthResult VerifyResult { get; set; } = new AuthResult.Success();

        public List<(string Email, string Code)> Verified { get; } = [];

        public Task InitializeAsync(CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AuthResult> SendCodeAsync(EmailAddress email, CancellationToken cancellationToken) =>
            Task.FromResult<AuthResult>(new AuthResult.Success());

        public Task<AuthResult> VerifyCodeAsync(EmailAddress email, SignInCode code, CancellationToken cancellationToken)
        {
            Verified.Add((email.Value, code.Value));
            return Task.FromResult(VerifyResult);
        }

        public Task SignOutAsync() => Task.CompletedTask;

        public Task<SessionRenewal> RenewAsync(CancellationToken cancellationToken) => Task.FromResult(SessionRenewal.Renewed);

        public Task EndSessionAsync() => Task.CompletedTask;
    }
}

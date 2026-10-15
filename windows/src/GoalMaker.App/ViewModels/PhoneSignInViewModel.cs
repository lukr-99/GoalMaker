using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Auth;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The QR code on the sign-in screen (ADR 0020, docs/sign-in.md). It starts a request, shows its scan
/// secret as a QR code with the match number the phone must pick, and asks every two seconds whether
/// the phone answered. An approval brings a
/// one-time code, which signs in through the same call as an emailed code and starts the PC's week.
/// A code that ran out, or that the phone declined, is not replaced by itself: the owner asks for a
/// new one, so a PC left on the sign-in screen doesn't keep making requests.
/// </summary>
public sealed partial class PhoneSignInViewModel : ObservableObject
{
    public static readonly TimeSpan PollEvery = TimeSpan.FromSeconds(2);

    private readonly IPcSignIn pcSignIn;
    private readonly IAuthGateway auth;
    private readonly SignInWatch watch;
    private readonly IStrings strings;
    private readonly string device;
    private readonly Func<DateTimeOffset> now;
    private readonly Func<TimeSpan, CancellationToken, Task> delay;
    private CancellationTokenSource? running;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasCode))]
    private string? qrText;

    /// <summary>The number the owner picks on the phone, shown with the code; null without one.</summary>
    [ObservableProperty]
    private string? matchText;

    [ObservableProperty]
    private string statusText = string.Empty;

    [ObservableProperty]
    private bool canMakeNewCode;

    public PhoneSignInViewModel(
        IPcSignIn pcSignIn,
        IAuthGateway auth,
        SignInWatch watch,
        IStrings strings,
        string device,
        Func<DateTimeOffset> now,
        Func<TimeSpan, CancellationToken, Task> delay)
    {
        this.pcSignIn = pcSignIn;
        this.auth = auth;
        this.watch = watch;
        this.strings = strings;
        this.device = device;
        this.now = now;
        this.delay = delay;
        StepsText = strings.Get("SignIn.Phone.Steps");
    }

    public bool HasCode => QrText is not null;

    public string StepsText { get; }

    /// <summary>The run in progress, for tests to wait on.</summary>
    public Task Running { get; private set; } = Task.CompletedTask;

    /// <summary>The sign-in screen is up: show a code unless one is already showing.</summary>
    public void Start()
    {
        if (running is null)
        {
            Begin();
        }
    }

    /// <summary>The sign-in screen went away: stop asking.</summary>
    public void Stop()
    {
        running?.Cancel();
        running?.Dispose();
        running = null;
        QrText = null;
        MatchText = null;
    }

    [RelayCommand]
    private void MakeNewCode()
    {
        Stop();
        Begin();
    }

    private void Begin()
    {
        running = new CancellationTokenSource();
        Running = RunAsync(running.Token);
    }

    private async Task RunAsync(CancellationToken cancellationToken)
    {
        CanMakeNewCode = false;
        QrText = null;
        StatusText = strings.Get("SignIn.Phone.Making");
        try
        {
            var start = await pcSignIn.StartAsync(device, cancellationToken);
            if (start is not PcSignInStart.Started request)
            {
                End(start switch
                {
                    PcSignInStart.Unavailable => "SignIn.Phone.Unavailable",
                    PcSignInStart.Busy => "SignIn.Phone.Busy",
                    PcSignInStart.Offline => "SignIn.Phone.Offline",
                    _ => "SignIn.Phone.Failed",
                });
                return;
            }

            QrText = request.QrText;
            MatchText = request.MatchNumber.ToString(CultureInfo.InvariantCulture);
            StatusText = strings.Get("SignIn.Phone.Waiting", (int)request.Lifetime.TotalMinutes);
            var deadline = now() + request.Lifetime;
            while (true)
            {
                await delay(PollEvery, cancellationToken);
                var state = await pcSignIn.PickUpAsync(request, cancellationToken);
                cancellationToken.ThrowIfCancellationRequested();
                switch (state)
                {
                    case PcSignInState.Approved approved:
                        await SignInAsync(approved);
                        return;
                    case PcSignInState.Declined:
                        End("SignIn.Phone.Declined");
                        return;
                    case PcSignInState.Expired:
                        End("SignIn.Phone.Expired");
                        return;
                    default:
                        // Waiting, offline for a moment, or a code the server couldn't make yet: the
                        // approval is kept for the next ask. The phone can't approve after the
                        // deadline, and the ask after it would have caught one just before.
                        if (now() >= deadline)
                        {
                            End("SignIn.Phone.Expired");
                            return;
                        }

                        break;
                }
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            // The screen went away or a new code was asked for.
        }
    }

    private async Task SignInAsync(PcSignInState.Approved approved)
    {
        QrText = null;
        MatchText = null;
        StatusText = strings.Get("SignIn.Phone.SigningIn");
        var result = await auth.VerifyCodeAsync(approved.Email, approved.Code, CancellationToken.None);
        if (result is AuthResult.Success)
        {
            // Like an emailed code, this starts the PC's week (docs/sign-in.md).
            watch.RecordSignIn();
            StatusText = string.Empty;
            return;
        }

        End(result is AuthResult.Offline ? "SignIn.Phone.Offline" : "SignIn.Phone.Failed");
    }

    private void End(string key)
    {
        QrText = null;
        MatchText = null;
        StatusText = strings.Get(key);
        CanMakeNewCode = key != "SignIn.Phone.Unavailable";
    }
}

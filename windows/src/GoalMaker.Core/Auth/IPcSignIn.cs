namespace GoalMaker.Core.Auth;

/// <summary>
/// The PC's half of the QR sign-in (ADR 0020): starts a request the phone can approve, then asks
/// whether it did. The Supabase adapter calls the pc-sign-in Edge Function; tests use a fake.
/// </summary>
public interface IPcSignIn
{
    Task<PcSignInStart> StartAsync(string device, CancellationToken cancellationToken);

    Task<PcSignInState> PickUpAsync(PcSignInStart.Started request, CancellationToken cancellationToken);
}

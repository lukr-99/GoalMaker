using GoalMaker.Core.Account;

namespace GoalMaker.Core.Auth;

/// <summary>Where a QR sign-in request stands, as the pc-sign-in function answers it.</summary>
public abstract record PcSignInState
{
    private PcSignInState()
    {
    }

    public sealed record Waiting : PcSignInState;

    public sealed record Declined : PcSignInState;

    /// <summary>Ran out, was picked up already, or is unknown: the server does not say which.</summary>
    public sealed record Expired : PcSignInState;

    /// <summary>The phone approved: a one-time code for the owner, used like an emailed one.</summary>
    public sealed record Approved(EmailAddress Email, SignInCode Code) : PcSignInState
    {
        public override string ToString() => $"Approved {{ Email = {Email.Value} }}";
    }

    public sealed record Offline : PcSignInState;

    public sealed record Failed : PcSignInState;
}

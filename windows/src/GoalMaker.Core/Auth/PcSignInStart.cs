namespace GoalMaker.Core.Auth;

/// <summary>What starting a QR sign-in gave: a request to show, or why there is none.</summary>
public abstract record PcSignInStart
{
    private PcSignInStart()
    {
    }

    /// <summary>
    /// A request. <paramref name="ScanSecret"/> goes in the QR code; <paramref name="PickupSecret"/>
    /// never leaves this PC. <paramref name="Lifetime"/> is the server's, so the PC's clock can't stretch it.
    /// </summary>
    public sealed record Started(string Id, string ScanSecret, string PickupSecret, TimeSpan Lifetime) : PcSignInStart
    {
        public string QrText => PcSignInCode.QrText(ScanSecret);

        // Never written to a log with its secrets.
        public override string ToString() => $"Started {{ Id = {Id}, Lifetime = {Lifetime} }}";
    }

    /// <summary>This server has no QR sign-in (the function isn't deployed).</summary>
    public sealed record Unavailable : PcSignInStart;

    /// <summary>Too many PCs are asking at once.</summary>
    public sealed record Busy : PcSignInStart;

    public sealed record Offline : PcSignInStart;

    public sealed record Failed : PcSignInStart;
}

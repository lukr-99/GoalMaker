namespace GoalMaker.Core.Updates;

/// <summary>The outcome of checking a release manifest: signature first, then content.</summary>
public abstract record ManifestCheck
{
    private ManifestCheck()
    {
    }

    public sealed record Valid(ReleaseManifest Manifest) : ManifestCheck;

    public sealed record BadSignature : ManifestCheck;

    public sealed record BadManifest(string Reason) : ManifestCheck;
}

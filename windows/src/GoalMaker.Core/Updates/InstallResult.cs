namespace GoalMaker.Core.Updates;

/// <summary>What happened when installing an available update.</summary>
public abstract record InstallResult
{
    private InstallResult()
    {
    }

    public sealed record InstallerStarted : InstallResult;

    public sealed record DownloadCorrupted : InstallResult;

    public sealed record Failed(string Detail) : InstallResult;
}

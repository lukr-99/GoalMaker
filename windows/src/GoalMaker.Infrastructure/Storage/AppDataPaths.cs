namespace GoalMaker.Infrastructure.Storage;

/// <summary>
/// Where this build keeps its local files. Development builds use their own folder, so a dev build
/// and the installed release never share a session or settings (like Android's .debug app ID).
/// </summary>
public sealed class AppDataPaths
{
    public AppDataPaths(bool isDevBuild)
        : this(Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            isDevBuild ? "GoalMaker-dev" : "GoalMaker"))
    {
    }

    public AppDataPaths(string root) => Root = root;

    public string Root { get; }

    public string Session => Path.Combine(Root, "session.bin");

    public string Settings => Path.Combine(Root, "settings.json");

    /// <summary>A dev build's own replica when it keeps everything on this PC, never mixed with a synced one.</summary>
    public string LocalReplica => Path.Combine(Root, "replica-local.db");

    public string Updates => Path.Combine(Root, "updates");

    /// <summary>
    /// The device replica (ADR 0007), one file per backend, so a dev build switched to another
    /// Supabase project never mixes its rows with the first one's.
    /// </summary>
    public string ReplicaFor(string backendUrl)
    {
        var digest = System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(backendUrl.Trim().TrimEnd('/')));
        return Path.Combine(Root, $"replica-{Convert.ToHexStringLower(digest)[..12]}.db");
    }

    public string EnsureRoot()
    {
        Directory.CreateDirectory(Root);
        return Root;
    }

    /// <summary>Deletes downloaded installers left from an earlier update; they are never reused.</summary>
    public void ClearUpdates()
    {
        try
        {
            if (Directory.Exists(Updates))
            {
                Directory.Delete(Updates, recursive: true);
            }
        }
        catch (IOException)
        {
            // Still in use by a running installer; the next start clears it.
        }
        catch (UnauthorizedAccessException)
        {
            // Same as above.
        }
    }
}

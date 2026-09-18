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

    public string Updates => Path.Combine(Root, "updates");

    public string EnsureRoot()
    {
        Directory.CreateDirectory(Root);
        return Root;
    }
}

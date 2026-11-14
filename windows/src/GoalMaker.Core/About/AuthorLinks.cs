namespace GoalMaker.Core.About;

/// <summary>
/// Who made GoalMaker and where to reach them, shown on the About page (docs/about.md). The one place
/// these are written in the PC app; contracts/vectors/about.json holds the same values for the phone.
/// The links open in the browser, so the app itself sends nothing anywhere.
/// </summary>
public static class AuthorLinks
{
    public const string Name = "Lukáš Krejčí";

    public const string InstagramHandle = "lukas9krejci";
    public const string InstagramUrl = "https://www.instagram.com/" + InstagramHandle + "/";

    public const string GitHubHandle = "lukr-99";
    public const string GitHubUrl = "https://github.com/" + GitHubHandle;

    /// <summary>GoalMaker's own repository, where bugs and ideas go as issues.</summary>
    public const string Repository = GitHubHandle + "/GoalMaker";
    public const string IssuesUrl = "https://github.com/" + Repository + "/issues";

    public static AuthorLink Instagram { get; } = new("instagram", InstagramUrl, "@" + InstagramHandle);

    public static AuthorLink GitHub { get; } = new("github", GitHubUrl, GitHubHandle);

    public static AuthorLink Issues { get; } = new("issues", IssuesUrl, Repository);

    /// <summary>In the order the author card shows them.</summary>
    public static IReadOnlyList<AuthorLink> All { get; } = [Instagram, GitHub, Issues];
}

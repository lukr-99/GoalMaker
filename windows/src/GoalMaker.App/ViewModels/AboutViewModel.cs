using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.About;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The About page (docs/about.md): this build's facts, who made GoalMaker and the links to reach them,
/// what the app keeps and where, and the credits. The links open in the browser; nothing here talks to
/// the network itself.
/// </summary>
public sealed partial class AboutViewModel
{
    public const string Ofl = "SIL OFL 1.1";
    private const string Mit = "MIT";
    private const string Apache = "Apache 2.0";

    private readonly Action<string> openInBrowser;

    public AboutViewModel(AppInfo appInfo, IStrings strings, Action<string> openInBrowser)
    {
        this.openInBrowser = openInBrowser;
        Version = appInfo.Version;
        IsDevBuild = appInfo.IsDevBuild;
        Kind = strings.Get(appInfo.IsDevBuild ? "About.DevBuild" : "About.Release");
        Backend = appInfo.Backend.Url;
        IsLocalOnly = appInfo.LocalOnly;
        CreditGroups = Credits(strings);
    }

    /// <summary>Asks the shell to go back to Settings, where About is opened from.</summary>
    public event EventHandler? BackRequested;

    public string Version { get; }

    public bool IsDevBuild { get; }

    /// <summary>Release or Dev build, in words.</summary>
    public string Kind { get; }

    public string Backend { get; }

    /// <summary>A dev build that never signs in or syncs: everything stays on this PC.</summary>
    public bool IsLocalOnly { get; }

    public bool Syncs => !IsLocalOnly;

    public string AuthorName => AuthorLinks.Name;

    public string AuthorMonogram => Monogram.Of(AuthorLinks.Name);

    public string InstagramHandle => AuthorLinks.Instagram.Handle;

    public string GitHubHandle => AuthorLinks.GitHub.Handle;

    public string IssuesHandle => AuthorLinks.Issues.Handle;

    public IReadOnlyList<AboutCreditGroup> CreditGroups { get; }

    [RelayCommand]
    private void OpenInstagram() => openInBrowser(AuthorLinks.Instagram.Url);

    [RelayCommand]
    private void OpenGitHub() => openInBrowser(AuthorLinks.GitHub.Url);

    [RelayCommand]
    private void OpenIssues() => openInBrowser(AuthorLinks.Issues.Url);

    [RelayCommand]
    private void Back() => BackRequested?.Invoke(this, EventArgs.Empty);

    // What the PC app ships that is not its own code, as the projects' package references and fonts/
    // list them, with each licence. Keep it in step when a package comes or goes (docs/about.md).
    private static IReadOnlyList<AboutCreditGroup> Credits(IStrings strings) =>
    [
        new(strings.Get("About.Credits.Licence"),
        [
            new("GoalMaker", strings.Get("About.Credit.GoalMaker"), "PolyForm Noncommercial 1.0.0"),
        ]),
        new(strings.Get("About.Credits.Fonts"),
        [
            new("Archivo", strings.Get("About.Credit.Archivo"), Ofl),
            new("Plus Jakarta Sans", strings.Get("About.Credit.Jakarta"), Ofl),
            new("Space Grotesk", strings.Get("About.Credit.Grotesk"), Ofl),
            new("Outfit", strings.Get("About.Credit.Outfit"), Ofl),
        ]),
        new(strings.Get("About.Credits.Icons"),
        [
            new("Fluent System Icons", strings.Get("About.Credit.FluentIcons"), Mit),
        ]),
        new(strings.Get("About.Credits.Libraries"),
        [
            new("WPF UI", strings.Get("About.Credit.WpfUi"), Mit),
            new("H.NotifyIcon", strings.Get("About.Credit.NotifyIcon"), Mit),
            new("DotNetLib.Tray", strings.Get("About.Credit.Tray"), strings.Get("About.Credit.OwnKit")),
            new("CommunityToolkit.Mvvm", strings.Get("About.Credit.Mvvm"), Mit),
            new("NHotkey", strings.Get("About.Credit.Hotkey"), Apache),
            new("Supabase C#", strings.Get("About.Credit.Supabase"), Mit),
            new("Microsoft.Data.Sqlite", strings.Get("About.Credit.Sqlite"), Mit),
            new("QRCoder", strings.Get("About.Credit.QrCoder"), Mit),
        ]),
    ];
}

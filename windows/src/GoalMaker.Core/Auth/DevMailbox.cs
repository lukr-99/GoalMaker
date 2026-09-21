using System.Text.RegularExpressions;

namespace GoalMaker.Core.Auth;

/// <summary>
/// Where a dev build finds the sign-in code it was just sent (docs/sign-in.md,
/// contracts/vectors/dev-mailbox.json). Only the local Supabase stack keeps its mail where a build
/// may read it: it serves its API on 55321 and the mailbox that caught the mail on 55324, both over
/// plain http. Anything else, the cloud project above all, has no mailbox here.
/// </summary>
public static partial class DevMailbox
{
    /// <summary>The port the local stack's API listens on (supabase/config.toml).</summary>
    public const int ApiPort = 55321;

    /// <summary>The port its mailbox listens on.</summary>
    public const int MailPort = 55324;

    /// <summary>The mailbox behind a backend address, or null when it isn't the local stack.</summary>
    public static string? Of(string? backend) =>
        Uri.TryCreate(backend, UriKind.Absolute, out var url) && url.Scheme == Uri.UriSchemeHttp && url.Port == ApiPort
            ? $"http://{url.Host}:{MailPort}"
            : null;

    /// <summary>The sign-in code in a message: the first run of exactly six digits.</summary>
    public static string? CodeIn(string? message) =>
        message is not null && Code().Match(message) is { Success: true } found ? found.Value : null;

    [GeneratedRegex(@"(?<!\d)\d{6}(?!\d)")]
    private static partial Regex Code();
}

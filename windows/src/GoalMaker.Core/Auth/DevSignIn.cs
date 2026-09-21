using System.Text.RegularExpressions;

namespace GoalMaker.Core.Auth;

/// <summary>
/// How a dev build gets past the sign-in screen without a mailbox in the way (docs/sign-in.md,
/// contracts/vectors/dev-sign-in.json). Only the local Supabase stack has this door: it catches every
/// message it sends in a mailbox on 55324, beside its API on 55321, where a build may read the code
/// and fill it in. Anything else, the cloud project above all, has no mailbox and no dev account.
/// </summary>
public static partial class DevSignIn
{
    /// <summary>The account a dev build offers, so trying things out never costs the owner's own.</summary>
    public const string Email = "dev@goalmaker.test";

    /// <summary>The port the local stack's API listens on (supabase/config.toml).</summary>
    public const int ApiPort = 55321;

    /// <summary>The port its mailbox listens on.</summary>
    public const int MailPort = 55324;

    /// <summary>The mailbox behind a backend address, or null when it isn't the local stack.</summary>
    public static string? MailboxOf(string? backend) =>
        Uri.TryCreate(backend, UriKind.Absolute, out var url) && url.Scheme == Uri.UriSchemeHttp && url.Port == ApiPort
            ? $"http://{url.Host}:{MailPort}"
            : null;

    /// <summary>The sign-in code in a message: the first run of exactly six digits.</summary>
    public static string? CodeIn(string? message) =>
        message is not null && SixDigits().Match(message) is { Success: true } found ? found.Value : null;

    [GeneratedRegex(@"(?<!\d)\d{6}(?!\d)")]
    private static partial Regex SixDigits();
}

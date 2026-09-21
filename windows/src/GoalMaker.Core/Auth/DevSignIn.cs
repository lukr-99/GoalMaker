using System.Text.RegularExpressions;

namespace GoalMaker.Core.Auth;

/// <summary>
/// How a dev build gets past the sign-in screen without a mailbox in the way (docs/sign-in.md,
/// contracts/vectors/dev-sign-in.json). Only the local Supabase stack has these doors: it takes
/// <see cref="Code"/> for <see cref="Email"/> without sending anything, and it keeps the mail for
/// every other address where a build may read it, on 55324 beside its API on 55321, both over plain
/// http. Anything else, the cloud project above all, has neither.
/// </summary>
public static partial class DevSignIn
{
    /// <summary>The address the local stack lets in with a fixed code (supabase/config.toml).</summary>
    public const string Email = "dev@goalmaker.test";

    /// <summary>The code it takes for that address, which no mail ever carries.</summary>
    public const string Code = "424242";

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

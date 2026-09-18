using System.Text.RegularExpressions;

namespace GoalMaker.Core.Account;

/// <summary>A trimmed, plausibly valid email address. Supabase Auth does the real check.</summary>
public sealed partial record EmailAddress
{
    private EmailAddress(string value) => Value = value;

    public string Value { get; }

    public static EmailAddress? Parse(string text)
    {
        var trimmed = text.Trim();
        return trimmed.Length <= 254 && Pattern().IsMatch(trimmed) ? new EmailAddress(trimmed) : null;
    }

    public override string ToString() => Value;

    [GeneratedRegex(@"^[^@\s]+@[^@\s]+\.[^@\s]+\z", RegexOptions.CultureInvariant)]
    private static partial Regex Pattern();
}

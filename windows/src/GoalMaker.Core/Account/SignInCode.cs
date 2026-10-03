using System.Text.RegularExpressions;

namespace GoalMaker.Core.Account;

/// <summary>The 6-digit code Supabase Auth emails for sign-in (supabase/config.toml: otp_length).</summary>
public sealed partial record SignInCode
{
    public const int Length = 6;

    private SignInCode(string value) => Value = value;

    public string Value { get; }

    /// <summary>Accepts ASCII digits with optional spaces ("123 456"); returns null otherwise.</summary>
    public static SignInCode? Parse(string text)
    {
        var digits = string.Concat(text.Where(character => !char.IsWhiteSpace(character)));
        return digits.Length == Length && digits.All(char.IsAsciiDigit) ? new SignInCode(digits) : null;
    }

    /// <summary>
    /// The code in text copied from elsewhere, like the email's notification or subject
    /// (contracts/vectors/sign-in-code.json, 'find'): the one standalone six-digit run, or null when there
    /// is none or two different ones, since a guess could sign in with the wrong code.
    /// </summary>
    public static SignInCode? Find(string text)
    {
        var codes = Code().Matches(text).Select(match => match.Groups[1].Value + match.Groups[2].Value).Distinct().ToList();
        return codes.Count == 1 ? new SignInCode(codes[0]) : null;
    }

    public override string ToString() => Value;

    // Six ASCII digits standing alone, optionally split once as 123 456 or 123-456: not part of a longer
    // run of digits or letters, a time like 10:30, a phone number's groups or a decimal.
    [GeneratedRegex(@"(?<![\p{L}0-9:+.\-/])(?<![0-9][ \-])([0-9]{3})[ \-]?([0-9]{3})(?![\p{L}0-9:])(?![ \-][0-9])")]
    private static partial Regex Code();
}

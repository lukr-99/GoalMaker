namespace GoalMaker.Core.Account;

/// <summary>The 6-digit code Supabase Auth emails for sign-in (supabase/config.toml: otp_length).</summary>
public sealed record SignInCode
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

    public override string ToString() => Value;
}

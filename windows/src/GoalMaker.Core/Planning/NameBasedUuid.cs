using System.Security.Cryptography;
using System.Text;

namespace GoalMaker.Core.Planning;

/// <summary>
/// A UUID version 5 (RFC 9562): the same name in the same namespace always gives the same id, so two
/// devices that make "the same" row offline make one row once they sync (docs/repeating.md,
/// docs/reminders.md).
/// </summary>
public static class NameBasedUuid
{
    public static string Of(string @namespace, string name)
    {
        var space = Convert.FromHexString(@namespace.Replace("-", string.Empty, StringComparison.Ordinal));
        var hash = SHA1.HashData([.. space, .. Encoding.UTF8.GetBytes(name)]);
        hash[6] = (byte)((hash[6] & 0x0F) | 0x50);
        hash[8] = (byte)((hash[8] & 0x3F) | 0x80);
        var hex = Convert.ToHexStringLower(hash, 0, 16);
        return $"{hex[..8]}-{hex[8..12]}-{hex[12..16]}-{hex[16..20]}-{hex[20..]}";
    }
}

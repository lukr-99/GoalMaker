using System.Text;

namespace GoalMaker.Core.Startup;

/// <summary>
/// The <c>startupprofiles://register?...</c> link that asks Startup Profiles to add GoalMaker
/// (that app's integration contract; spec, story 84). Building the link is all GoalMaker does: the
/// link opens Startup Profiles' own confirmation window, where the owner picks the profiles, and
/// nothing is written to that app's files or keys from here.
/// </summary>
public static class StartupProfilesLink
{
    /// <summary>The scheme Startup Profiles registers for itself.</summary>
    public const string Scheme = "startupprofiles";

    /// <summary>The link for this request, with every value escaped for a query string.</summary>
    public static string Register(StartupProfilesRequest request)
    {
        var link = new StringBuilder($"{Scheme}://register");
        var first = true;
        void Field(string name, string? value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                return;
            }

            link.Append(first ? '?' : '&').Append(name).Append('=').Append(Uri.EscapeDataString(value));
            first = false;
        }

        Field("appId", request.AppId);
        Field("name", request.Name);
        Field("target", request.Target);
        Field("args", request.Arguments);
        Field("publisher", request.Publisher);
        if (request.SupportsMinimized)
        {
            Field("supportsMinimized", "true");
        }

        return link.ToString();
    }
}

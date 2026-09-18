namespace GoalMaker.Core.Backend;

/// <summary>Which Supabase project the app talks to: its API URL and publishable key.</summary>
public sealed record BackendEnvironment(string Url, string PublishableKey)
{
    public bool IsConfigured => !string.IsNullOrWhiteSpace(Url) && !string.IsNullOrWhiteSpace(PublishableKey);
}

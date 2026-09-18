using GoalMaker.Core.Backend;
using Supabase;

namespace GoalMaker.Infrastructure.Auth;

/// <summary>Builds the one Supabase client for the chosen backend. Called only by the composition root.</summary>
public static class SupabaseClientFactory
{
    public static Client Create(BackendEnvironment environment, string sessionPath) =>
        new(
            environment.Url,
            environment.PublishableKey,
            new SupabaseOptions
            {
                AutoRefreshToken = true,
                AutoConnectRealtime = false,
                SessionHandler = new ProtectedFileSessionPersistence(sessionPath),
            });
}

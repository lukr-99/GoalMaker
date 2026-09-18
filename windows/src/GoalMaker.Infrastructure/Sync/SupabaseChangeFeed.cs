using GoalMaker.Core.Sync;
using Supabase.Realtime;
using Supabase.Realtime.PostgresChanges;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>
/// Listens to Supabase Realtime for changes to the synced tables and reports each as a nudge; the
/// payload is never applied, the next pull fetches the rows (docs/sync.md). Every (re)join is a nudge
/// too, since changes made while disconnected produced no events. Failures are quiet: the periodic
/// sync still runs.
/// </summary>
public sealed class SupabaseChangeFeed(Supabase.Client client, SyncedTableCatalog catalog, Action onChange) : IAsyncDisposable
{
    private RealtimeChannel? channel;

    public async Task StartAsync(string accessToken)
    {
        await StopAsync().ConfigureAwait(false);
        try
        {
            client.Realtime.SetAuth(accessToken);
            await client.Realtime.ConnectAsync().ConfigureAwait(false);
            var joined = client.Realtime.Channel("goalmaker-sync");
            foreach (var table in catalog.Tables)
            {
                joined.OnPostgresChange(
                    (_, _) => onChange(),
                    PostgresChangesOptions.ListenType.All,
                    new PostgresChangesFilter { Schema = "public", Table = table.Name });
            }

            joined.AddStateChangedHandler((_, state) =>
            {
                if (state == Constants.ChannelState.Joined)
                {
                    onChange();
                }
            });
            await joined.Subscribe().ConfigureAwait(false);
            channel = joined;
        }
        catch (Exception error) when (error is not OutOfMemoryException)
        {
            // Realtime is a speed-up only; sync still runs on writes and on its timer.
        }
    }

    public void UpdateToken(string accessToken)
    {
        try
        {
            client.Realtime.SetAuth(accessToken);
        }
        catch (Exception error) when (error is not OutOfMemoryException)
        {
            // Same as above.
        }
    }

    public async Task StopAsync()
    {
        if (channel is null)
        {
            return;
        }

        try
        {
            channel.Unsubscribe();
            client.Realtime.Remove(channel);
            client.Realtime.Disconnect();
        }
        catch (Exception error) when (error is not OutOfMemoryException)
        {
            // Already gone.
        }

        channel = null;
        await Task.CompletedTask.ConfigureAwait(false);
    }

    public ValueTask DisposeAsync() => new(StopAsync());
}

using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>The contracts built into the app (contracts/schemas/synced-tables.json).</summary>
public static class ContractResources
{
    public static SyncedTableCatalog SyncedTables()
    {
        using var stream = typeof(ContractResources).Assembly.GetManifestResourceStream("GoalMaker.Contracts.synced-tables.json")
            ?? throw new InvalidOperationException("synced-tables.json is not built in.");
        return SyncedTableCatalog.Load(stream);
    }
}

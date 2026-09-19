using GoalMaker.Core.Design;
using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>The contracts built into the app (synced-tables.json, the design tokens in themes.json and the logo in logo.json).</summary>
public static class ContractResources
{
    public static DesignTokens Themes()
    {
        using var stream = typeof(ContractResources).Assembly.GetManifestResourceStream("GoalMaker.Contracts.themes.json")
            ?? throw new InvalidOperationException("themes.json is not built in.");
        return DesignTokens.Load(stream);
    }

    /// <summary>The mark's shape, drawn in each theme's logo colors.</summary>
    public static LogoMark Logo()
    {
        using var stream = typeof(ContractResources).Assembly.GetManifestResourceStream("GoalMaker.Contracts.logo.json")
            ?? throw new InvalidOperationException("logo.json is not built in.");
        return LogoMark.Load(stream);
    }

    public static SyncedTableCatalog SyncedTables()
    {
        using var stream = typeof(ContractResources).Assembly.GetManifestResourceStream("GoalMaker.Contracts.synced-tables.json")
            ?? throw new InvalidOperationException("synced-tables.json is not built in.");
        return SyncedTableCatalog.Load(stream);
    }
}

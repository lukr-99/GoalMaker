using GoalMaker.Core.Design;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>The contracts built into the app: synced-tables.json, themes.json, logo.json and the review prompts.</summary>
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

    /// <summary>The review prompts the app ships (docs/reviews.md).</summary>
    public static PromptLibrary Prompts()
    {
        using var stream = typeof(ContractResources).Assembly.GetManifestResourceStream("GoalMaker.Contracts.prompts.json")
            ?? throw new InvalidOperationException("prompts.json is not built in.");
        return PromptLibrary.Load(stream);
    }

    public static SyncedTableCatalog SyncedTables()
    {
        using var stream = typeof(ContractResources).Assembly.GetManifestResourceStream("GoalMaker.Contracts.synced-tables.json")
            ?? throw new InvalidOperationException("synced-tables.json is not built in.");
        return SyncedTableCatalog.Load(stream);
    }
}

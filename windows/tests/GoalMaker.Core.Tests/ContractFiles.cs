using System.Text.Json;

namespace GoalMaker.Core.Tests;

/// <summary>Loads a vector file from the repository's contracts/ folder, shared with the Android tests.</summary>
internal static class ContractFiles
{
    public static JsonDocument Load(string relativePath)
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "contracts")))
        {
            directory = directory.Parent;
        }

        if (directory is null)
        {
            throw new DirectoryNotFoundException("contracts/ not found above " + AppContext.BaseDirectory);
        }

        return JsonDocument.Parse(File.ReadAllBytes(Path.Combine(directory.FullName, "contracts", relativePath)));
    }
}

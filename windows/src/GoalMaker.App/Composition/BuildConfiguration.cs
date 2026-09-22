using System.Reflection;
using GoalMaker.Core.Backend;

namespace GoalMaker.App.Composition;

/// <summary>What this build was compiled with (see GoalMaker.App.csproj and Directory.Build.props).</summary>
public sealed record BuildConfiguration(
    string Version,
    bool IsDevBuild,
    BackendEnvironment DefaultBackend,
    string ManifestPublicKey,
    string UpdateUrl,
    string Publisher)
{
    /// <summary>Distinct per build kind so a dev build and the installed release can run side by side.</summary>
    public string InstanceName => IsDevBuild ? "GoalMaker-dev" : "GoalMaker";

    /// <summary>
    /// The update channel needs both the key that signs the manifest and the repository page whose
    /// GitHub Releases carry it (ADR 0010). Without either one, the build has no channel.
    /// </summary>
    public bool HasUpdateChannel => !string.IsNullOrWhiteSpace(ManifestPublicKey) && !string.IsNullOrWhiteSpace(UpdateUrl);

    public static BuildConfiguration FromAssembly(Assembly assembly)
    {
        string Metadata(string key) =>
            assembly.GetCustomAttributes<AssemblyMetadataAttribute>().FirstOrDefault(item => item.Key == key)?.Value ?? string.Empty;

        var version = assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion ?? "0.0.0-dev";
        return new BuildConfiguration(
            Version: version,
            IsDevBuild: !string.Equals(Metadata("GoalMaker.ReleaseBuild"), "true", StringComparison.OrdinalIgnoreCase),
            DefaultBackend: new BackendEnvironment(Metadata("GoalMaker.SupabaseUrl"), Metadata("GoalMaker.SupabaseKey")),
            ManifestPublicKey: Metadata("GoalMaker.ManifestPublicKey"),
            UpdateUrl: Metadata("GoalMaker.UpdateUrl"),
            Publisher: assembly.GetCustomAttribute<AssemblyCompanyAttribute>()?.Company ?? string.Empty);
    }
}

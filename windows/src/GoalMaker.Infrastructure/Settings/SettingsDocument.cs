using GoalMaker.Core.Settings;

namespace GoalMaker.Infrastructure.Settings;

/// <summary>The settings file's shape. Version 1.</summary>
public sealed record SettingsDocument
{
    public int Version { get; init; } = 1;

    public ThemeMode ThemeMode { get; init; } = ThemeMode.System;

    public string? BackendUrl { get; init; }

    public string? BackendKey { get; init; }
}

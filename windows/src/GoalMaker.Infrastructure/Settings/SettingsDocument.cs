using GoalMaker.Core.Settings;

namespace GoalMaker.Infrastructure.Settings;

/// <summary>The settings file's shape. Version 1; appearance fields added in M2 read as defaults from older files.</summary>
public sealed record SettingsDocument
{
    public int Version { get; init; } = 1;

    public string? ThemeId { get; init; }

    public ThemeMode ThemeMode { get; init; } = ThemeMode.System;

    public bool PureBlack { get; init; }

    public ReduceMotion ReduceMotion { get; init; } = ReduceMotion.System;

    public bool CompletionSound { get; init; }

    public string? BackendUrl { get; init; }

    public string? BackendKey { get; init; }

    public WindowPlacement? MainWindowPlacement { get; init; }
}

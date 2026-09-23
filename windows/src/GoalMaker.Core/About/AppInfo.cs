using GoalMaker.Core.Backend;

namespace GoalMaker.Core.About;

/// <summary>
/// Facts about this build, shown in Settings → About. <see cref="LocalOnly"/> is a dev build that neither
/// signs in nor syncs and keeps everything on this PC (docs/sign-in.md).
/// </summary>
public sealed record AppInfo(
    string Version,
    bool IsDevBuild,
    BackendEnvironment Backend,
    BackendEnvironment DefaultBackend,
    bool LocalOnly = false);

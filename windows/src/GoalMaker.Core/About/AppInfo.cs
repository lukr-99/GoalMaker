using GoalMaker.Core.Backend;

namespace GoalMaker.Core.About;

/// <summary>Facts about this build, shown in Settings → About.</summary>
public sealed record AppInfo(
    string Version,
    bool IsDevBuild,
    BackendEnvironment Backend,
    BackendEnvironment DefaultBackend);

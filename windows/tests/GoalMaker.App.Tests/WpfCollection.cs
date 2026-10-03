namespace GoalMaker.App.Tests;

/// <summary>
/// The tests on <see cref="WpfApp"/>'s application. They run one at a time after the others, so no
/// test on a thread of its own meets an application it did not expect.
/// </summary>
[CollectionDefinition(nameof(WpfCollection), DisableParallelization = true)]
public sealed class WpfCollection;

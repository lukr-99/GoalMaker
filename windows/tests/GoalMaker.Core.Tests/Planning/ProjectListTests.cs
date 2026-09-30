using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>How long a project keeps done items on its board (supabase/migrations/0018_board_archive.sql).</summary>
public sealed class ProjectListTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));

    public void Dispose() => test.Dispose();

    [Fact]
    public void ANewProjectKeepsDoneItemsFourteenDays()
    {
        var projects = Projects();

        var project = projects.Add(new ProjectDraft("GoalMaker"))!;

        Assert.Equal(14, project.ArchiveAfterDays);
        Assert.Equal(14, projects.Get(project.Id)!.ArchiveAfterDays);
    }

    [Fact]
    public void TheDaysGoFromOneTo365OrNever()
    {
        var projects = Projects();
        var id = projects.Add(new ProjectDraft("GoalMaker"))!.Id;

        Assert.True(projects.SetArchiveAfterDays(id, 1));
        Assert.True(projects.SetArchiveAfterDays(id, 365));
        Assert.False(projects.SetArchiveAfterDays(id, 0));
        Assert.False(projects.SetArchiveAfterDays(id, 366));
        Assert.Equal(365, projects.Get(id)!.ArchiveAfterDays);

        Assert.True(projects.SetArchiveAfterDays(id, null));
        Assert.Null(projects.Get(id)!.ArchiveAfterDays);
    }

    [Fact]
    public void EditingAProjectKeepsItsDays()
    {
        var projects = Projects();
        var id = projects.Add(new ProjectDraft("GoalMaker"))!.Id;
        projects.SetArchiveAfterDays(id, 30);

        projects.Update(id, new ProjectDraft("GoalMaker 2"));

        Assert.Equal(30, projects.Get(id)!.ArchiveAfterDays);
    }

    private ProjectList Projects() => new(test.Replica, new NewRows(test.Catalog, () => TestReplica.Owner, time), () => { });
}

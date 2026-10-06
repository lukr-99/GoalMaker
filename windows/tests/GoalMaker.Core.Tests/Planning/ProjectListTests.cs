using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>
/// How long a project keeps done items on its board (supabase/migrations/0018_board_archive.sql), and
/// the key its items read by (docs/projects.md, "Item ids").
/// </summary>
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

    [Fact]
    public void AKeyIsUpperCasedAndBlankClearsIt()
    {
        var projects = Projects();
        var id = projects.Add(new ProjectDraft("GoalMaker"))!.Id;

        Assert.True(projects.SetItemKey(id, " gm "));
        Assert.Equal("GM", projects.Get(id)!.ItemKey);
        Assert.Equal("GM", (string?)test.Replica.Get("projects", id)!["item_key"]);

        Assert.True(projects.SetItemKey(id, "  "));
        Assert.Null(projects.Get(id)!.ItemKey);
        Assert.Null(test.Replica.Get("projects", id)!["item_key"]);
    }

    [Fact]
    public void AKeyThatIsNotOneIsRefused()
    {
        var projects = Projects();
        var id = projects.Add(new ProjectDraft("GoalMaker"))!.Id;
        projects.SetItemKey(id, "GM");

        Assert.False(projects.SetItemKey(id, "G"));
        Assert.False(projects.SetItemKey(id, "1GM"));
        Assert.False(projects.SetItemKey(id, "ABCDEFG"));
        Assert.False(projects.SetItemKey(id, "G-M"));
        Assert.Equal("GM", projects.Get(id)!.ItemKey);
    }

    [Fact]
    public void AKeyAnotherProjectUsesIsRefusedInAnyCase()
    {
        var projects = Projects();
        var goalMaker = projects.Add(new ProjectDraft("GoalMaker"))!.Id;
        var other = projects.Add(new ProjectDraft("Game master"))!.Id;
        projects.SetItemKey(goalMaker, "GM");

        Assert.False(projects.SetItemKey(other, "gm"));
        Assert.Null(projects.Get(other)!.ItemKey);
        Assert.True(projects.IsKeyTaken("gm", other));
        Assert.False(projects.IsKeyTaken("GM", goalMaker));
        Assert.Equal(["GM"], projects.OtherKeys(other));

        // A project keeps its own key, and a deleted one frees it.
        Assert.True(projects.SetItemKey(goalMaker, "gm"));
        projects.Delete(goalMaker);
        Assert.True(projects.SetItemKey(other, "GM"));
    }

    private ProjectList Projects() => new(test.Replica, new NewRows(test.Catalog, () => TestReplica.Owner, time), () => { });
}

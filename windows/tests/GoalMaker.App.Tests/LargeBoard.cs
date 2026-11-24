using System.Text.Json.Nodes;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// A planner the size the owner's boards are heading for (GM-121): 40 projects over 5 areas, 3,000
/// project items (one project holds 600 of them), 20 tags on about a third of the items, milestones,
/// item numbers, done items both recent and long gone, dropped ones, and 1,000 plain tasks. The real
/// board had 25 projects and about 1,200 items in November 2026. The data is the same on every run.
/// </summary>
internal static class LargeBoard
{
    public const int ProjectCount = 40;
    public const int ItemCount = 3000;
    public const int BigProjectItems = 600;
    public const string BigProject = "Atlas";

    private static readonly string[] Words =
    [
        "sync", "widget", "board", "search", "filter", "calendar", "habit", "review", "reminder", "login",
        "theme", "crash", "export", "backup", "toast", "tray", "hotkey", "layout", "scroll", "focus",
        "milestone", "connector", "release", "signing", "migration", "notes", "tag", "area", "column", "card",
        "příliš", "žluťoučký", "kůň", "úpěl", "ďábelské", "ódy",
    ];

    /// <summary>Fills <paramref name="planner"/> in one transaction, so it takes a few seconds and raises one change per table.</summary>
    public static void Fill(TestPlanner planner)
    {
        var random = new Random(121);
        planner.Replica.InTransaction(() =>
        {
            var areas = Enumerable.Range(1, 5).Select(i => planner.Areas.Create($"Area {i}")!.Id).ToList();
            var tags = Enumerable.Range(1, 20).Select(i => planner.Tags.FindOrCreate($"tag{i}")!).ToList();
            var projects = new List<ProjectItem>();
            for (var i = 0; i < ProjectCount; i++)
            {
                var name = i == 0 ? BigProject : $"Project {i:00}";
                var project = planner.Projects.Add(new ProjectDraft(name)
                {
                    AreaId = areas[i % areas.Count],
                    Status = i % 13 == 12 ? ProjectRules.Paused : ProjectRules.Active,
                })!;
                if (i % 3 == 0)
                {
                    planner.Projects.SetItemKey(project.Id, i == 0 ? "ATL" : $"P{i:00}");
                }

                for (var m = 0; m < 3; m++)
                {
                    planner.Projects.AddMilestone(project.Id, $"M{m}");
                }

                projects.Add(project);
            }

            var numbers = new Dictionary<string, int>(StringComparer.Ordinal);
            for (var i = 0; i < ItemCount; i++)
            {
                var project = i < BigProjectItems ? projects[0] : projects[1 + (i % (ProjectCount - 1))];
                var title = Sentence(random, 3 + random.Next(6));
                var task = planner.Tasks.Add(title, Sentence(random, 10 + random.Next(30)));
                var kind = (i % 5) switch { 0 => ProjectRules.Idea, 1 => ProjectRules.Bug, _ => ProjectRules.Task };
                planner.Tasks.SetProject(task.Id, project.Id, kind);
                planner.Tasks.SetPriority(task.Id, ProjectRules.Priorities[i % ProjectRules.Priorities.Count]);
                if (i % 3 == 0)
                {
                    planner.Tasks.SetTags(task.Id, [$"tag{1 + (i % tags.Count)}"]);
                }

                var row = planner.Replica.Get("tasks", task.Id)!;
                numbers[project.Id] = numbers.GetValueOrDefault(project.Id) + 1;
                row["item_number"] = numbers[project.Id];
                row["made_by"] = i % 2 == 0 ? ProjectRules.Claude : ProjectRules.Owner;
                if (i % 7 == 0)
                {
                    row["area_id"] = areas[(i / 7) % areas.Count];
                }

                // 40% done: a fifth of those finished this week, the rest long ago; 5% dropped.
                switch (i % 20)
                {
                    case < 8:
                        row["status"] = "done";
                        row["board_column"] = ProjectRules.Done;
                        row["completed_at"] = i % 20 < 2 ? "2026-09-16T10:00:00.000Z" : "2026-03-01T10:00:00.000Z";
                        break;
                    case 8:
                        row["status"] = "dropped";
                        break;
                    default:
                        row["board_column"] = ProjectRules.BoardColumns[i % 3];
                        break;
                }

                planner.Replica.Put("tasks", row);
            }

            for (var i = 0; i < 1000; i++)
            {
                planner.Tasks.Add(Sentence(random, 4));
            }
        });
    }

    /// <summary>The 600-item project, which is where a slow board shows first.</summary>
    public static ProjectItem Big(TestPlanner planner) => planner.Projects.All().Single(project => project.Name == BigProject);

    private static string Sentence(Random random, int words) =>
        string.Join(' ', Enumerable.Range(0, words).Select(_ => Words[random.Next(Words.Length)]));

    private static TaskItem Add(this TaskList tasks, string title, string notes) =>
        tasks.Add(new Core.Composer.ComposerDraft(title, null, null, [], null, null, false, false, null, null, []), notes)!;
}

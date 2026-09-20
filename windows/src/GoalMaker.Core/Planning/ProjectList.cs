using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's projects and their milestones, read from the replica and changed through its outbox
/// (docs/projects.md). The items themselves are tasks, so <see cref="TaskList"/> moves them around the
/// board. Every write asks for a sync.
/// </summary>
public sealed class ProjectList
{
    private const string Table = "projects";
    private const string MilestoneTable = "project_milestones";
    private const int MaxName = 120;
    private const int MaxDescription = 2000;
    private const int MaxLink = 500;
    private const int MaxNotes = 20_000;
    private static readonly string[] Statuses = [ProjectRules.Active, ProjectRules.Paused, ProjectRules.Finished];
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public ProjectList(IReplica replica, NewRows rows, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table is Table or MilestoneTable)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to a project or a milestone.</summary>
    public event EventHandler? Changed;

    /// <summary>Every project that isn't deleted, active ones first, in the owner's order.</summary>
    public IReadOnlyList<ProjectItem> All() =>
        [.. replica.All(Table).Select(ToItem).Where(project => !project.Deleted)
            .OrderBy(project => project.Status == ProjectRules.Finished)
            .ThenBy(project => project.Status == ProjectRules.Paused)
            .ThenBy(project => project.Position)
            .ThenBy(project => project.Name, StringComparer.OrdinalIgnoreCase)];

    /// <summary>The project with this id, or null when it is gone.</summary>
    public ProjectItem? Get(string id) =>
        replica.Get(Table, id) is { } row && ToItem(row) is { Deleted: false } project ? project : null;

    /// <summary>The project with this name, whatever the case; null when there is none.</summary>
    public ProjectItem? Find(string name) =>
        All().FirstOrDefault(project => string.Equals(project.Name.Trim(), name.Trim(), StringComparison.OrdinalIgnoreCase));

    /// <summary>
    /// The project the composer's <c>+Project</c> names, made if it isn't there yet, the way
    /// <c>@Area</c> makes an area (docs/composer.md). Null when the name is blank.
    /// </summary>
    public ProjectItem? FindOrCreate(string name) => Find(name) ?? Add(new ProjectDraft(name));

    /// <summary>Every milestone that isn't deleted, in the order each project keeps them.</summary>
    public IReadOnlyList<ProjectMilestone> Milestones() =>
        [.. replica.All(MilestoneTable).Select(ToMilestone).Where(milestone => !milestone.Deleted)
            .OrderBy(milestone => milestone.Position)
            .ThenBy(milestone => milestone.Name, StringComparer.OrdinalIgnoreCase)];

    /// <summary>The milestones of one project.</summary>
    public IReadOnlyList<ProjectMilestone> MilestonesOf(string projectId) =>
        [.. Milestones().Where(milestone => milestone.ProjectId == projectId)];

    /// <summary>Adds a project at the end. Null when it has no name.</summary>
    public ProjectItem? Add(ProjectDraft draft)
    {
        if (Check(draft) is not { } clean)
        {
            return null;
        }

        var values = Values(clean);
        values["position"] = All().Select(project => project.Position).DefaultIfEmpty(-1).Max() + 1;
        if (rows.Create(Table, values) is not { } row)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    /// <summary>Changes a project to what the draft says. False when it has no name or the project is gone.</summary>
    public bool Update(string id, ProjectDraft draft) => Check(draft) is { } clean && Change(Table, id, row =>
    {
        foreach (var (column, value) in Values(clean))
        {
            row[column] = value;
        }
    });

    /// <summary>Marks a project active, paused or done.</summary>
    public bool SetStatus(string id, string status) =>
        Statuses.Contains(status) && Change(Table, id, row => row["status"] = status);

    /// <summary>Deletes a project softly; its items stay as plain tasks.</summary>
    public bool Delete(string id) => Change(Table, id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>Adds a milestone at the end of a project's list. Null when it has no name or the project is gone.</summary>
    public ProjectMilestone? AddMilestone(string projectId, string name)
    {
        var clean = Clip(name, MaxName);
        if (clean is null || Get(projectId) is null)
        {
            return null;
        }

        var values = new Dictionary<string, JsonNode?>
        {
            ["project_id"] = projectId,
            ["name"] = clean,
            ["position"] = MilestonesOf(projectId).Select(milestone => milestone.Position).DefaultIfEmpty(-1).Max() + 1,
        };
        if (rows.Create(MilestoneTable, values) is not { } row)
        {
            return null;
        }

        replica.Queue(MilestoneTable, row);
        requestSync();
        return ToMilestone(row);
    }

    public bool RenameMilestone(string id, string name) =>
        Clip(name, MaxName) is { } clean && Change(MilestoneTable, id, row => row["name"] = clean);

    /// <summary>Deletes a milestone softly; the items that carried it keep their column.</summary>
    public bool DeleteMilestone(string id) =>
        Change(MilestoneTable, id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    private static string? Clip(string? text, int length)
    {
        var trimmed = text?.Trim() ?? string.Empty;
        if (trimmed.Length > length)
        {
            trimmed = trimmed[..length];
        }

        return trimmed.Length == 0 ? null : trimmed;
    }

    private static ProjectDraft? Check(ProjectDraft draft)
    {
        if (Clip(draft.Name, MaxName) is not { } name)
        {
            return null;
        }

        return draft with
        {
            Name = name,
            Description = draft.Description.Length > MaxDescription ? draft.Description[..MaxDescription] : draft.Description,
            Status = Statuses.Contains(draft.Status) ? draft.Status : ProjectRules.Active,
            RepositoryUrl = Clip(draft.RepositoryUrl, MaxLink),
            LocalFolder = Clip(draft.LocalFolder, MaxLink),
            Notes = draft.Notes.Length > MaxNotes ? draft.Notes[..MaxNotes] : draft.Notes,
        };
    }

    private static Dictionary<string, JsonNode?> Values(ProjectDraft draft) => new()
    {
        ["name"] = draft.Name,
        ["description"] = draft.Description,
        ["area_id"] = draft.AreaId,
        ["status"] = draft.Status,
        ["repository_url"] = draft.RepositoryUrl,
        ["local_folder"] = draft.LocalFolder,
        ["notes"] = draft.Notes,
    };

    private static ProjectItem ToItem(JsonObject row) => new((string?)row[SyncedTable.Id] ?? string.Empty, (string?)row["name"] ?? string.Empty)
    {
        Description = (string?)row["description"] ?? string.Empty,
        AreaId = (string?)row["area_id"],
        Status = (string?)row["status"] ?? ProjectRules.Active,
        RepositoryUrl = (string?)row["repository_url"],
        LocalFolder = (string?)row["local_folder"],
        Notes = (string?)row["notes"] ?? string.Empty,
        Position = row["position"] is JsonValue place && place.TryGetValue<double>(out var at) ? at : 0,
        Deleted = row[SyncedTable.DeletedAt] is not null,
    };

    private static ProjectMilestone ToMilestone(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["project_id"] ?? string.Empty,
        (string?)row["name"] ?? string.Empty,
        row["position"] is JsonValue place && place.TryGetValue<double>(out var at) ? at : 0,
        row[SyncedTable.DeletedAt] is not null);

    private bool Change(string table, string id, Action<JsonObject> edit)
    {
        if (replica.Get(table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        edit(row);
        replica.Queue(table, row);
        requestSync();
        return true;
    }
}

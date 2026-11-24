namespace GoalMaker.Core.Planning;

/// <summary>
/// What a search on the Projects page looks for (docs/projects.md, "Searching items"). Forgiving: case
/// and accents are ignored and every word must be in the item's title or notes, in any order. An item id,
/// GM-12 or #12, finds that item. Filter words narrow it, and the chips beside the box add the same:
/// <list type="bullet">
/// <item><c>#tag</c>: the item has that tag (#12, digits only, is an id);</item>
/// <item><c>is:open</c>, <c>is:done</c>, <c>is:dropped</c>, and <c>is:task</c>, <c>is:idea</c>, <c>is:bug</c>;</item>
/// <item><c>type:</c> task, idea or bug; <c>p:</c> or <c>priority:</c> urgent, high, normal or low;</item>
/// <item><c>m:</c> or <c>milestone:</c> a milestone's name, like m:M3.</item>
/// </list>
/// A filter word that names nothing it knows is searched as a plain word. Any whitespace parts words, and
/// double quotes keep a phrase together: "board sync" is one phrase, m:"Q4 launch" and #"to read" a
/// milestone and a tag with spaces. Within one kind the values are alternatives (type:bug type:idea, or
/// a chip and a typed word, finds either); tags must all be there; different kinds must all hold. The words are folded once,
/// when the query is read, and an item's text once per change of it (<see cref="ProjectBoardIndex"/>),
/// so a keystroke only compares strings.
/// </summary>
public sealed class ProjectSearch
{
    private static readonly string[] TypeNames = [ProjectRules.Task, ProjectRules.Idea, ProjectRules.Bug];
    private static readonly string[] StateNames = ["open", "done", "dropped"];

    private ProjectSearch(
        ItemId? id,
        IReadOnlyList<string> words,
        IReadOnlySet<string> tags,
        IReadOnlySet<string> types,
        IReadOnlySet<string> priorities,
        IReadOnlySet<string> states,
        IReadOnlySet<string> milestones)
    {
        Id = id;
        Words = words;
        Tags = tags;
        ItemTypes = types;
        Priorities = priorities;
        States = states;
        Milestones = milestones;
    }

    /// <summary>The search that keeps everything: an empty query and no filters.</summary>
    public static ProjectSearch Everything { get; } = new(null, [], Set(), Set(), Set(), Set(), Set());

    /// <summary>The item id the query names, or null.</summary>
    public ItemId? Id { get; }

    /// <summary>The words to find, folded: what the results light up.</summary>
    public IReadOnlyList<string> Words { get; }

    /// <summary>Folded tag names the item must all have.</summary>
    public IReadOnlySet<string> Tags { get; }

    public IReadOnlySet<string> ItemTypes { get; }

    public IReadOnlySet<string> Priorities { get; }

    /// <summary>open, done or dropped: the item is in one of them.</summary>
    public IReadOnlySet<string> States { get; }

    /// <summary>Folded milestone names: the item's milestone is one of them.</summary>
    public IReadOnlySet<string> Milestones { get; }

    /// <summary>Whether it keeps every item: nothing typed and no filter.</summary>
    public bool IsEmpty => Id is null && Words.Count == 0 && !HasFilters;

    /// <summary>Whether a filter narrows it: a filter word or a chip, not just words or an id.</summary>
    public bool HasFilters => Tags.Count + ItemTypes.Count + Priorities.Count + States.Count + Milestones.Count > 0;

    /// <summary>Reads what was typed: words, an id and filter words, in any order.</summary>
    public static ProjectSearch Of(string? query)
    {
        ItemId? id = null;
        var words = new List<string>();
        HashSet<string> tags = Set(), types = Set(), priorities = Set(), states = Set(), milestones = Set();
        foreach (var (_, token) in Tokens(query))
        {
            var folded = ArchiveRules.Fold(token);
            if (id is null && ProjectRules.ParseItemId(token) is { } named)
            {
                id = named;
            }
            else if (folded.Length > 1 && folded[0] == '#')
            {
                tags.Add(folded[1..]);
            }
            else if (Filter(folded, types, priorities, states, milestones))
            {
                // A filter word, kept in its set.
            }
            else if (folded.Length > 0)
            {
                words.Add(folded);
            }
        }

        return id is null && words.Count + tags.Count + types.Count + priorities.Count + states.Count + milestones.Count == 0
            ? Everything
            : new ProjectSearch(id, words, tags, types, priorities, states, milestones);
    }

    /// <summary>
    /// The query's parts: split at any whitespace outside double quotes. Text is the part as typed,
    /// quotes and all; Value is without the quotes.
    /// </summary>
    public static IReadOnlyList<(string Text, string Value)> Tokens(string? query)
    {
        var tokens = new List<(string, string)>();
        var text = new System.Text.StringBuilder();
        var value = new System.Text.StringBuilder();
        var quoted = false;
        foreach (var character in query ?? string.Empty)
        {
            if (char.IsWhiteSpace(character) && !quoted)
            {
                Flush();
                continue;
            }

            text.Append(character);
            if (character == '"')
            {
                quoted = !quoted;
            }
            else
            {
                value.Append(character);
            }
        }

        Flush();
        return tokens;

        void Flush()
        {
            if (value.Length > 0)
            {
                tokens.Add((text.ToString(), value.ToString()));
            }

            text.Clear();
            value.Clear();
        }
    }

    /// <summary>
    /// This search narrowed by the chips beside the box; a null chip leaves it as it is. A tag or a
    /// milestone is by name.
    /// </summary>
    public ProjectSearch With(string? itemType = null, string? priority = null, string? state = null, string? tag = null, string? milestone = null)
    {
        if (itemType is null && priority is null && state is null && tag is null && milestone is null)
        {
            return this;
        }

        return new ProjectSearch(
            Id,
            Words,
            Plus(Tags, tag is null ? null : ArchiveRules.Fold(tag)),
            Plus(ItemTypes, itemType),
            Plus(Priorities, priority),
            Plus(States, state),
            Plus(Milestones, milestone is null ? null : ArchiveRules.Fold(milestone)));
    }

    /// <summary>
    /// Whether <paramref name="item"/> is found. <paramref name="foldedText"/> is its title and notes
    /// folded, <paramref name="foldedTags"/> its tags' names and <paramref name="foldedMilestone"/> its
    /// milestone's name, folded. <paramref name="projectKey"/> is its project's key;
    /// <paramref name="inProject"/> lets #12 name the item of the project on show even when that project has a key.
    /// </summary>
    public bool Finds(TaskItem item, string foldedText, string? projectKey, bool inProject, IReadOnlySet<string>? foldedTags = null, string? foldedMilestone = null)
    {
        if (Id is { } wanted && !ProjectRules.IsItem(wanted, projectKey, item.ItemNumber, inProject))
        {
            return false;
        }

        foreach (var word in Words)
        {
            if (!foldedText.Contains(word, StringComparison.Ordinal))
            {
                return false;
            }
        }

        if (ItemTypes.Count > 0 && !ItemTypes.Contains(item.ItemType))
        {
            return false;
        }

        if (Priorities.Count > 0 && !Priorities.Contains(item.Priority))
        {
            return false;
        }

        if (States.Count > 0 && !States.Contains(StateOf(item)))
        {
            return false;
        }

        if (Milestones.Count > 0 && (foldedMilestone is null || !Milestones.Contains(foldedMilestone)))
        {
            return false;
        }

        foreach (var tag in Tags)
        {
            if (foldedTags is null || !foldedTags.Contains(tag))
            {
                return false;
            }
        }

        return true;
    }

    private static string StateOf(TaskItem item) => item.State switch
    {
        TaskState.Done => "done",
        TaskState.Dropped => "dropped",
        _ => "open",
    };

    // is:, type:, p:, priority:, m: and milestone:, when what follows names something.
    private static bool Filter(string folded, HashSet<string> types, HashSet<string> priorities, HashSet<string> states, HashSet<string> milestones)
    {
        var colon = folded.IndexOf(':', StringComparison.Ordinal);
        if (colon <= 0 || colon == folded.Length - 1)
        {
            return false;
        }

        var (name, value) = (folded[..colon], folded[(colon + 1)..]);
        switch (name)
        {
            case "is" when StateNames.Contains(value):
                states.Add(value);
                return true;
            case "is" or "type" when TypeNames.Contains(value):
                types.Add(value);
                return true;
            case "p" or "priority" when ProjectRules.Priorities.Contains(value):
                priorities.Add(value);
                return true;
            case "m" or "milestone":
                milestones.Add(value);
                return true;
            default:
                return false;
        }
    }

    private static HashSet<string> Set() => new(StringComparer.Ordinal);

    private static HashSet<string> Plus(IReadOnlySet<string> set, string? value)
    {
        var more = new HashSet<string>(set, StringComparer.Ordinal);
        if (value is not null)
        {
            more.Add(value);
        }

        return more;
    }
}

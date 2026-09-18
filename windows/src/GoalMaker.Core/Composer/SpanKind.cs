namespace GoalMaker.Core.Composer;

/// <summary>What a recognized part of a composer line is (docs/composer.md).</summary>
public enum SpanKind
{
    Date,
    Time,
    Repeat,
    Tag,
    Area,
    Project,
    Priority,
    Idea,
    Command,
}

namespace GoalMaker.Core.Composer;

/// <summary>
/// What one composer line says (docs/composer.md). <see cref="Repeat"/> is an RRULE subset;
/// <see cref="Spans"/> lists every recognized part so the composer can highlight it and a chip can
/// remove its own text.
/// </summary>
public sealed record ComposerDraft(
    string Title,
    DateOnly? PlannedDate,
    TimeOnly? PlannedTime,
    IReadOnlyList<string> Tags,
    string? Area,
    string? Project,
    bool TopPriority,
    bool Idea,
    string? Repeat,
    ComposerCommand? Command,
    IReadOnlyList<ComposerSpan> Spans);

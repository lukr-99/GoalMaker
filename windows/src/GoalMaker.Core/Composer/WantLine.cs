namespace GoalMaker.Core.Composer;

/// <summary>
/// What the Wants bar reads from a typed line (docs/composer.md): the title, the reason after
/// "because", a price with its currency and a picked wait in days. Null where the line says nothing.
/// </summary>
public sealed record WantLine(string Title, string? Reason, double? Price, string? Currency, int? WaitDays);

namespace GoalMaker.Core.Backup;

/// <summary>
/// What a restore did, or would do (docs/backup.md): rows that were not here, rows the file was
/// newer for, and rows the file was older for, which a restore leaves alone.
/// </summary>
public sealed record RestoreReport(int Added = 0, int Updated = 0, int Kept = 0)
{
    public int Total => Added + Updated + Kept;

    public int Changed => Added + Updated;

    public static RestoreReport operator +(RestoreReport left, RestoreReport right) =>
        new(left.Added + right.Added, left.Updated + right.Updated, left.Kept + right.Kept);
}

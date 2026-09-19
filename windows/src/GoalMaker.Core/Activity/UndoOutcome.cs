namespace GoalMaker.Core.Activity;

/// <summary>What asking the server to undo a change came to (supabase/migrations/0007, undo_activity).</summary>
public enum UndoOutcome
{
    /// <summary>The row is back the way the entry found it.</summary>
    Undone,

    /// <summary>The row changed after this entry, so undoing it would throw later work away.</summary>
    ChangedSince,

    /// <summary>Someone undid it already, perhaps on the other device.</summary>
    AlreadyUndone,

    /// <summary>The entry is gone, or the change can't be undone.</summary>
    NotPossible,
}

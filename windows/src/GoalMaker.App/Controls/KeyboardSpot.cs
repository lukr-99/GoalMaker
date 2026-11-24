namespace GoalMaker.App.Controls;

/// <summary>
/// Where the keyboard was in a list that is redrawn from new rows (the Focus Board, its add panel): the
/// row's id, its place and which part of the row. A redraw drops the element that had the keyboard, so
/// the page puts it back on the same row, or on the row now in its place when that one has gone.
/// Forgotten once the keyboard leaves the list, so a later redraw (a sync) does not pull it back.
/// </summary>
public sealed class KeyboardSpot
{
    private string? id;
    private int index;

    /// <summary>Which part of the row had the keyboard, by its element name.</summary>
    public string Part { get; private set; } = string.Empty;

    public bool IsSet => id is not null;

    public void Remember(string rowId, int rowIndex, string part)
    {
        id = rowId;
        index = Math.Max(rowIndex, 0);
        Part = part;
    }

    public void Forget()
    {
        id = null;
        Part = string.Empty;
    }

    /// <summary>
    /// The place in <paramref name="ids"/> to put the keyboard back on: the same row if it is still there,
    /// else the one now in its place (the last when it was the end). Null when nothing is remembered or the
    /// list is empty.
    /// </summary>
    public int? Pick(IReadOnlyList<string> ids)
    {
        if (id is null || ids.Count == 0) return null;
        for (var i = 0; i < ids.Count; i++)
        {
            if (ids[i] == id) return i;
        }

        return Math.Min(index, ids.Count - 1);
    }
}

namespace GoalMaker.App.ViewModels;

/// <summary>What the live part of a tile on the Places page draws, whichever says the most about the place.</summary>
public enum PlaceTileLook
{
    /// <summary>A line of text, such as "3 planned".</summary>
    Line,

    /// <summary>A ring with "done / total" beside it (Today, Habits, Goals).</summary>
    Ring,

    /// <summary>One big number with a word after it (Inbox, ready Wants).</summary>
    Number,

    /// <summary>Today's Tally time as one stacked bar, with the total under it.</summary>
    Tally,

    /// <summary>The Letter is here: the tile turns into the hero one.</summary>
    Letter,
}

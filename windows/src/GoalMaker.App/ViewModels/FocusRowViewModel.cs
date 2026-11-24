using System.Windows.Media;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One row on the Focus Board: its place as shown (1 at the top), the title, and a line with the date
/// (the deadline, else the planned day; late ones in the danger color) and the area with its color.
/// </summary>
public sealed class FocusRowViewModel
{
    public string Id { get; }
    public string Title { get; }
    public double? FocusPosition { get; }

    /// <summary>Where the row sits on the board as shown, 1 being the top.</summary>
    public int Place { get; }

    /// <summary>Whether Up can move it: it is not the first row shown.</summary>
    public bool CanMoveUp { get; }

    /// <summary>Whether Down can move it: it is not the last row shown.</summary>
    public bool CanMoveDown { get; }

    public string DateText { get; }
    public bool HasDate => DateText.Length > 0;

    /// <summary>Whether the date has passed.</summary>
    public bool IsLate { get; }

    public string? AreaName { get; }
    public bool HasArea => !string.IsNullOrEmpty(AreaName);
    public Brush? AreaBrush { get; }

    public bool HasDetails => HasDate || HasArea;

    /// <summary>The date and the area in one line, for screen readers and the tests.</summary>
    public string Subtitle => string.Join(" · ", new[] { DateText, AreaName }.Where(part => !string.IsNullOrEmpty(part)));

    public bool HasSubtitle => Subtitle.Length > 0;

    public FocusRowViewModel(TaskItem task, AreaItem? area, DateOnly today, int place = 1, int count = 1, Brush? areaBrush = null)
    {
        Id = task.Id;
        Title = task.Title;
        FocusPosition = task.FocusPosition;
        Place = place;
        CanMoveUp = place > 1;
        CanMoveDown = place < count;
        (DateText, IsLate) = FocusDate.For(task, today);
        AreaName = area?.Name;
        AreaBrush = areaBrush;
    }
}

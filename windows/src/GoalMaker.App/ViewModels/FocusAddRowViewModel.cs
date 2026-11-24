using System.Windows.Media;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One open task the add panel offers, with its date and area as on the board.</summary>
public sealed class FocusAddRowViewModel
{
    public string Id { get; }
    public string Title { get; }

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

    public FocusAddRowViewModel(TaskItem task, AreaItem? area, DateOnly today, Brush? areaBrush = null)
    {
        Id = task.Id;
        Title = task.Title;
        (DateText, IsLate) = FocusDate.For(task, today);
        AreaName = area?.Name;
        AreaBrush = areaBrush;
    }
}

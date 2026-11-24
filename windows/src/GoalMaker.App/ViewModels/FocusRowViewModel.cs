using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One row on the Focus Board: its place as shown (1 at the top), the title, and a line with the date
/// (the deadline, else the planned day; late ones in the danger color) and the area with its color.
/// </summary>
public sealed partial class FocusRowViewModel : ObservableObject
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

    /// <summary>What Narrator reads after the title: "Late" first for a late task, then the date and area.</summary>
    public string HelpText { get; }

    /// <summary>
    /// The done box. Ticking it completes the task, which leaves the board; when the task stays open (a
    /// repeating one moving on, a change that did nothing) the view model unticks it again.
    /// </summary>
    [ObservableProperty]
    private bool isDone;

    public FocusRowViewModel(TaskItem task, AreaItem? area, DateOnly today, int place, int count, string lateLabel, Brush? areaBrush = null)
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
        HelpText = FocusDate.HelpText(Subtitle, IsLate, lateLabel);
    }
}

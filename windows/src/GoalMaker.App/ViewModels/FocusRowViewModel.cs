using System.Globalization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One row on the Focus Board.</summary>
public sealed class FocusRowViewModel
{
    public string Id { get; }
    public string Title { get; }
    public string DateLabel { get; }
    public string? AreaName { get; }
    public double? FocusPosition { get; }

    public FocusRowViewModel(TaskItem task, AreaItem? area)
    {
        Id = task.Id;
        Title = task.Title;
        FocusPosition = task.FocusPosition;
        var date = task.Deadline ?? task.PlannedDate;
        var today = DateOnly.FromDateTime(DateTime.Today);
        DateLabel = date is { } d
            ? (d < today ? $"!! {d:d MMM}" : d.ToString("d MMM", CultureInfo.CurrentCulture))
            : string.Empty;
        AreaName = area?.Name;
    }

    public string Subtitle => string.Join(" · ", new[] { DateLabel, AreaName }.Where(s => !string.IsNullOrEmpty(s)));
    public bool HasSubtitle => !string.IsNullOrEmpty(Subtitle);
}

using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One task in a list: the done box, the title, and what else it says (time, the day when overdue,
/// area, repeat, top priority). Checking it and deleting it go through the list, which offers undo.
/// </summary>
public sealed partial class TaskRowViewModel : ObservableObject
{
    private readonly Action<TaskRowViewModel> complete;

    [ObservableProperty]
    private bool isDone;

    public TaskRowViewModel(TaskItem item, AreaItem? area, Brush? areaBrush, bool showDay, Action<TaskRowViewModel> complete, Action<TaskRowViewModel> delete)
    {
        this.complete = complete;
        Item = item;
        AreaName = area?.Name ?? string.Empty;
        AreaBrush = areaBrush;
        TimeText = item.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty;
        DayText = showDay && item.PlannedDate is { } day ? day.ToString("ddd d MMM", CultureInfo.CurrentCulture) : string.Empty;
        DeleteCommand = new RelayCommand(() => delete(this));
    }

    public TaskItem Item { get; }

    public string Title => Item.Title;

    public string TimeText { get; }

    public bool HasTime => TimeText.Length > 0;

    public string DayText { get; }

    public bool HasDay => DayText.Length > 0;

    public string AreaName { get; }

    public Brush? AreaBrush { get; }

    public bool HasArea => AreaName.Length > 0;

    public bool Repeats => Item.Recurrence is not null;

    public bool TopPriority => Item.TopPriority;

    public IRelayCommand DeleteCommand { get; }

    partial void OnIsDoneChanged(bool value)
    {
        if (value)
        {
            complete(this);
        }
    }
}

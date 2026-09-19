using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One task in a list: the done box, the title, and what else it says (time, the day when overdue,
/// area, repeat, top priority, a waiting reminder). Checking it and deleting it go through the list,
/// which offers undo; its menu sets and removes reminders (docs/reminders.md).
/// </summary>
public sealed partial class TaskRowViewModel : ObservableObject
{
    private readonly Action<TaskRowViewModel> complete;

    [ObservableProperty]
    private bool isDone;

    public TaskRowViewModel(
        TaskItem item,
        AreaItem? area,
        Brush? areaBrush,
        bool showDay,
        Action<TaskRowViewModel> complete,
        Action<TaskRowViewModel> delete,
        bool hasReminder = false,
        IReadOnlyList<ReminderChoice>? reminderChoices = null,
        Action<TaskRowViewModel>? open = null,
        IStrings? strings = null)
    {
        OpenCommand = new RelayCommand(() => open?.Invoke(this), () => open is not null);
        HasReminder = hasReminder;
        ReminderChoices = reminderChoices ?? [];
        this.complete = complete;
        Item = item;
        AreaName = area?.Name ?? string.Empty;
        AreaBrush = areaBrush;
        TimeText = item.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty;
        DayText = showDay && item.PlannedDate is { } day ? day.ToString("ddd d MMM", CultureInfo.CurrentCulture) : string.Empty;
        DeleteCommand = new RelayCommand(() => delete(this));
        Status = strings is null ? string.Empty : string.Join(", ", new[]
        {
            TopPriority ? strings.Get("Lists.TopPriority") : null,
            Repeats ? strings.Get("Lists.Repeats") : null,
            HasReminder ? strings.Get("Reminder.Waiting") : null,
        }.OfType<string>());
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

    /// <summary>Opens the task's detail page (M2-12).</summary>
    public IRelayCommand OpenCommand { get; }

    /// <summary>Whether a reminder is still waiting for this task.</summary>
    public bool HasReminder { get; }

    /// <summary>
    /// What the row's icons say, for screen readers: the flag, the repeat and the bell have no
    /// accessible text of their own, so the done box carries it as help text.
    /// </summary>
    public string Status { get; }

    public IReadOnlyList<ReminderChoice> ReminderChoices { get; }

    public bool CanRemind => ReminderChoices.Count > 0;

    partial void OnIsDoneChanged(bool value)
    {
        if (value)
        {
            complete(this);
        }
    }
}

using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A task step 1 of Plan tomorrow asks about: what it is, and the decision it shows now (read from
/// its state). Picking a decision goes through the ritual, which saves it at once.
/// </summary>
public sealed partial class ReviewRowViewModel : ObservableObject
{
    private readonly DateOnly today;
    private readonly IStrings strings;
    private readonly Action<ReviewRowViewModel, PlanDecision, DateOnly?> decide;

    [ObservableProperty]
    private bool isPicking;

    public ReviewRowViewModel(
        TaskItem item,
        AreaItem? area,
        Brush? areaBrush,
        DateOnly today,
        IStrings strings,
        Action<ReviewRowViewModel, PlanDecision, DateOnly?> decide)
    {
        this.today = today;
        this.strings = strings;
        this.decide = decide;
        Item = item;
        Update(item, area, areaBrush);
    }

    public TaskItem Item { get; private set; }

    public string Title => Item.Title;

    public PlanDecision Decision => PlanRules.Decision(Item, today);

    public string TimeText => Item.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty;

    public bool HasTime => Item.PlannedTime is not null;

    /// <summary>The day an overdue task was planned for.</summary>
    public string DayText => Item.PlannedDate is { } day && day < today ? day.ToString("ddd d MMM", CultureInfo.CurrentCulture) : string.Empty;

    public bool HasDay => DayText.Length > 0;

    public string AreaName { get; private set; } = string.Empty;

    public Brush? AreaBrush { get; private set; }

    public bool HasArea => AreaName.Length > 0;

    public bool Repeats => Item.Recurrence is not null;

    public bool TopPriority => Item.TopPriority;

    public bool IsTomorrow => Decision == PlanDecision.Tomorrow;

    public bool IsLater => Decision == PlanDecision.Later;

    public bool IsDone => Decision == PlanDecision.Done;

    public bool IsDropped => Decision == PlanDecision.Dropped;

    public bool IsUnplanned => Decision == PlanDecision.Unplanned;

    /// <summary>"Date", or the day it was planned for once it has one.</summary>
    public string DateText => IsLater && Item.PlannedDate is { } day ? day.ToString("ddd d MMM", CultureInfo.CurrentCulture) : strings.Get("Plan.Date");

    /// <summary>The first day the calendar offers: tomorrow (the same as choosing Tomorrow).</summary>
    public DateTime FirstPickableDate => today.AddDays(1).ToDateTime(TimeOnly.MinValue);

    /// <summary>The calendar's selection; picking a day plans the task for it.</summary>
    public DateTime? PickedDate
    {
        get => IsLater && Item.PlannedDate is { } day ? day.ToDateTime(TimeOnly.MinValue) : null;
        set
        {
            if (value is not { } picked)
            {
                return;
            }

            IsPicking = false;
            decide(this, PlanDecision.Later, DateOnly.FromDateTime(picked));
        }
    }

    /// <summary>Takes the task's latest state; every binding is refreshed, so a re-picked choice stays checked.</summary>
    public void Update(TaskItem item, AreaItem? area, Brush? areaBrush)
    {
        Item = item;
        AreaName = area?.Name ?? string.Empty;
        AreaBrush = areaBrush;
        OnPropertyChanged(string.Empty);
    }

    [RelayCommand]
    private void ChooseTomorrow() => decide(this, PlanDecision.Tomorrow, null);

    [RelayCommand]
    private void PickDate() => IsPicking = true;

    [RelayCommand]
    private void ChooseDone() => decide(this, PlanDecision.Done, null);

    [RelayCommand]
    private void ChooseDrop() => decide(this, PlanDecision.Dropped, null);
}

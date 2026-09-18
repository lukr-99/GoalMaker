using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>A task in step 2 of Plan tomorrow: one of tomorrow's (flag it) or from the Inbox (bring it in).</summary>
public sealed partial class PlanTaskViewModel : ObservableObject
{
    private readonly IStrings strings;

    public PlanTaskViewModel(
        TaskItem item,
        AreaItem? area,
        Brush? areaBrush,
        bool canPick,
        IStrings strings,
        Action<PlanTaskViewModel> togglePriority,
        Action<PlanTaskViewModel> planTomorrow)
    {
        this.strings = strings;
        Item = item;
        ToggleCommand = new RelayCommand(() => togglePriority(this));
        PlanTomorrowCommand = new RelayCommand(() => planTomorrow(this));
        Update(item, area, areaBrush, canPick);
    }

    public TaskItem Item { get; private set; }

    public string Title => Item.Title;

    public string TimeText => Item.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty;

    public bool HasTime => Item.PlannedTime is not null;

    public string AreaName { get; private set; } = string.Empty;

    public Brush? AreaBrush { get; private set; }

    public bool HasArea => AreaName.Length > 0;

    public bool Repeats => Item.Recurrence is not null;

    public bool TopPriority => Item.TopPriority;

    /// <summary>Flagged ones can always be cleared; others only while fewer than three are flagged.</summary>
    public bool CanToggle { get; private set; }

    public string PlanTomorrowName => strings.Get("Plan.ToTomorrow", Item.Title);

    public IRelayCommand ToggleCommand { get; }

    public IRelayCommand PlanTomorrowCommand { get; }

    public void Update(TaskItem item, AreaItem? area, Brush? areaBrush, bool canPick)
    {
        Item = item;
        AreaName = area?.Name ?? string.Empty;
        AreaBrush = areaBrush;
        CanToggle = item.TopPriority || canPick;
        OnPropertyChanged(string.Empty);
    }
}

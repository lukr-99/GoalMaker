using CommunityToolkit.Mvvm.ComponentModel;

namespace GoalMaker.App.ViewModels;

/// <summary>One weekday box in the habit editor: Monday is 0 and Sunday 6, as the weekday bitmask counts.</summary>
public sealed partial class HabitDayViewModel : ObservableObject
{
    private readonly Action changed;

    [ObservableProperty]
    private bool isChosen;

    public HabitDayViewModel(int index, string label, Action changed)
    {
        Index = index;
        Label = label;
        this.changed = changed;
    }

    public int Index { get; }

    public string Label { get; }

    /// <summary>Sets the box without telling the editor a person did it.</summary>
    public void Set(bool chosen) => IsChosen = chosen;

    partial void OnIsChosenChanged(bool value) => changed();
}

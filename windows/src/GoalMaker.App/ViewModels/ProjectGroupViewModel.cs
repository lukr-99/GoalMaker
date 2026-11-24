using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A heading in the project list: Pinned, an area, No area, Paused and done, or Items when a quick
/// search finds items. A heading that can fold hides its projects until it is opened (GM-122).
/// </summary>
public sealed partial class ProjectGroupViewModel : ObservableObject
{
    private readonly Action<ProjectGroupViewModel>? fold;

    [ObservableProperty]
    private string title = string.Empty;

    [ObservableProperty]
    private int count;

    [ObservableProperty]
    private bool isFolded;

    public ProjectGroupViewModel(string key, Action<ProjectGroupViewModel>? fold = null)
    {
        Key = key;
        this.fold = fold;
        FoldCommand = new RelayCommand(() => this.fold?.Invoke(this), () => this.fold is not null);
    }

    /// <summary>Which group it is: "pinned", an area's id, "none", "resting" or "items".</summary>
    public string Key { get; }

    /// <summary>Whether a click folds or opens the group.</summary>
    public bool CanFold => fold is not null;

    public IRelayCommand FoldCommand { get; }
}

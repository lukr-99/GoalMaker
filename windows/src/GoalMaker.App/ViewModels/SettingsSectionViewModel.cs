using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One entry of the Settings section list: its title, whether it is being read, and the jump to it.</summary>
public sealed partial class SettingsSectionViewModel : ObservableObject
{
    [ObservableProperty]
    private bool isCurrent;

    public SettingsSectionViewModel(string id, string title, string accessibleName, Action<string> jump)
    {
        Id = id;
        Title = title;
        AccessibleName = accessibleName;
        JumpCommand = new RelayCommand(() => jump(id));
    }

    public string Id { get; }

    public string Title { get; }

    /// <summary>What a screen reader says: "Jump to Updates".</summary>
    public string AccessibleName { get; }

    public IRelayCommand JumpCommand { get; }
}

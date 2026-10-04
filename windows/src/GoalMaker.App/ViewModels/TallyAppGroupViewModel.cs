using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One category in the Tally page's apps on this PC (docs/tally.md): its look and time, and the apps
/// that made it up, shown once it is opened. Whether it is open outlives a refresh of the page.
/// </summary>
public sealed partial class TallyAppGroupViewModel(string category, string name, string emoji, Brush? brush, string value, IReadOnlyList<TallyAppRowViewModel> apps, bool expanded, Action<string, bool> remember)
    : ObservableObject
{
    [ObservableProperty]
    private bool isExpanded = expanded;

    public string Category { get; } = category;

    public string Name { get; } = name;

    public string Emoji { get; } = emoji;

    public Brush? Brush { get; } = brush;

    public string Value { get; } = value;

    public IReadOnlyList<TallyAppRowViewModel> Apps { get; } = apps;

    partial void OnIsExpandedChanged(bool value) => remember(Category, value);

    [RelayCommand]
    private void Toggle() => IsExpanded = !IsExpanded;
}

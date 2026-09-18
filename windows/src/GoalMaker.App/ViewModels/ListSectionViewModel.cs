using CommunityToolkit.Mvvm.ComponentModel;

namespace GoalMaker.App.ViewModels;

/// <summary>A section of a list: an optional header (collapsible for Overdue) and its rows.</summary>
public sealed partial class ListSectionViewModel(string header, bool collapsible, bool expanded, IReadOnlyList<TaskRowViewModel> rows, Action<bool>? expandedChanged = null)
    : ObservableObject
{
    [ObservableProperty]
    private bool isExpanded = expanded;

    public string Header { get; } = header;

    public bool IsCollapsible { get; } = collapsible;

    /// <summary>A plain header: shown when there is one and it doesn't fold.</summary>
    public bool HasPlainHeader => Header.Length > 0 && !IsCollapsible;

    public IReadOnlyList<TaskRowViewModel> Rows { get; } = rows;

    partial void OnIsExpandedChanged(bool value) => expandedChanged?.Invoke(value);
}

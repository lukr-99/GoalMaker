using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Focus Board: a manually-ordered watchlist of open tasks. Items are reordered with Up/Down,
/// completed or removed from the board without deleting.
/// </summary>
public sealed partial class FocusViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly Action<Action> runOnUi;

    [ObservableProperty]
    private ObservableCollection<FocusRowViewModel> items = [];

    [ObservableProperty]
    private bool isEmpty = true;

    [ObservableProperty]
    private FocusRowViewModel? selected;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsAddClosed))]
    private bool isAddOpen;

    public bool IsAddClosed => !IsAddOpen;

    [ObservableProperty]
    private ObservableCollection<FocusAddRowViewModel> candidates = [];

    [ObservableProperty]
    private bool isCandidatesEmpty;

    [ObservableProperty]
    private string quickAddTitle = string.Empty;

    public FocusViewModel(TaskList tasks, AreaList areas, Action<Action> runOnUi)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.runOnUi = runOnUi;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    private void Refresh()
    {
        var all = tasks.All();
        var areaMap = areas.All().ToDictionary(a => a.Id);
        var board = FocusBoardRules.Board(all)
            .Select(t => new FocusRowViewModel(t, areaMap.GetValueOrDefault(t.AreaId ?? "")))
            .ToList();

        Items = new ObservableCollection<FocusRowViewModel>(board);
        IsEmpty = board.Count == 0;

        if (IsAddOpen) RefreshCandidates(all, areaMap);
    }

    private void RefreshCandidates(IReadOnlyList<TaskItem> all, Dictionary<string, AreaItem> areaMap)
    {
        var boardIds = Items.Select(i => i.Id).ToHashSet(StringComparer.Ordinal);
        var rows = all
            .Where(t => !t.Deleted && t.State == TaskState.Open && !t.FocusPosition.HasValue && !boardIds.Contains(t.Id))
            .Select(t => new FocusAddRowViewModel(t, areaMap.GetValueOrDefault(t.AreaId ?? "")))
            .ToList();
        Candidates = new ObservableCollection<FocusAddRowViewModel>(rows);
        IsCandidatesEmpty = rows.Count == 0;
    }

    [RelayCommand]
    private void Complete(FocusRowViewModel? row)
    {
        if (row is null) return;
        tasks.SetDone(row.Id, true);
    }

    [RelayCommand]
    private void RemoveFromBoard(FocusRowViewModel? row)
    {
        if (row is null) return;
        tasks.SetFocusPosition(row.Id, null);
    }

    [RelayCommand]
    private void ShowAddDialog()
    {
        var all = tasks.All();
        var areaMap = areas.All().ToDictionary(a => a.Id);
        RefreshCandidates(all, areaMap);
        IsAddOpen = true;
    }

    [RelayCommand]
    private void CloseAddDialog() => IsAddOpen = false;

    [RelayCommand]
    private void AddToBoard(FocusAddRowViewModel? candidate)
    {
        if (candidate is null) return;
        tasks.SetFocusPosition(candidate.Id, FocusBoardRules.AppendPosition(tasks.All()));
    }

    [RelayCommand]
    private void QuickAdd()
    {
        var title = QuickAddTitle.Trim();
        if (string.IsNullOrEmpty(title)) return;
        var pos = FocusBoardRules.AppendPosition(tasks.All());
        var item = tasks.Add(title);
        if (item is null) return;
        tasks.SetFocusPosition(item.Id, pos);
        QuickAddTitle = string.Empty;
    }

    [RelayCommand]
    private void MoveUp(FocusRowViewModel? row)
    {
        if (row is null) return;
        var index = Items.IndexOf(row);
        if (index <= 0) return;
        Move(index, index - 1);
    }

    [RelayCommand]
    private void MoveDown(FocusRowViewModel? row)
    {
        if (row is null) return;
        var index = Items.IndexOf(row);
        if (index < 0 || index >= Items.Count - 1) return;
        Move(index, index + 1);
    }

    private void Move(int fromIndex, int toIndex)
    {
        var id = Items[fromIndex].Id;
        tasks.SetFocusPosition(id, FocusBoardRules.PlaceAt(tasks.All(), id, toIndex + 1));
    }
}

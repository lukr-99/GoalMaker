using System.Collections.ObjectModel;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The areas and tags in the sidebar, as filters for every list (M2-11, spec story 9). Rebuilt when
/// areas, tags or the filter change; a filter whose area or tag was deleted is let go.
/// </summary>
public sealed partial class SidebarFiltersViewModel : ObservableObject
{
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ListFilterState filter;
    private readonly Func<string, Brush?> areaBrush;

    [ObservableProperty]
    private bool hasAreas;

    [ObservableProperty]
    private bool hasTags;

    /// <summary>Whether there is anything to filter by; without areas or tags the sidebar shows no Filter header.</summary>
    [ObservableProperty]
    private bool hasAny;

    [ObservableProperty]
    private bool isFiltering;

    public SidebarFiltersViewModel(AreaList areas, TagList tags, ListFilterState filter, Func<string, Brush?> areaBrush, Action<Action> runOnUi)
    {
        this.areas = areas;
        this.tags = tags;
        this.filter = filter;
        this.areaBrush = areaBrush;
        ClearCommand = new RelayCommand(filter.Clear);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public ObservableCollection<FilterOptionViewModel> Areas { get; } = [];

    public ObservableCollection<FilterOptionViewModel> Tags { get; } = [];

    public IRelayCommand ClearCommand { get; }

    public void Refresh()
    {
        // Archived areas leave the filters, and a filter on one falls away.
        var areaList = areas.Active();
        var tagList = tags.All();
        filter.Forget([.. areaList.Select(area => area.Id)], [.. tagList.Select(tag => tag.Id)]);
        var current = filter.Current;

        Areas.Clear();
        foreach (var area in areaList)
        {
            var label = area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name;
            Areas.Add(new FilterOptionViewModel(area.Id, label, areaBrush(area.ColorId), current.AreaId == area.Id, new RelayCommand(() => filter.ToggleArea(area.Id))));
        }

        Tags.Clear();
        foreach (var tag in tagList)
        {
            Tags.Add(new FilterOptionViewModel(tag.Id, "#" + tag.Name, null, current.TagId == tag.Id, new RelayCommand(() => filter.ToggleTag(tag.Id))));
        }

        HasAreas = Areas.Count > 0;
        HasTags = Tags.Count > 0;
        HasAny = HasAreas || HasTags;
        IsFiltering = !current.IsEmpty;
    }
}

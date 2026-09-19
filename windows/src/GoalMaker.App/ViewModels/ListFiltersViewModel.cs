using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The area and tag pickers above the lists, one filter for every list (M2-11, spec story 9, docs/lists.md).
/// Rebuilt when areas, tags or the filter change; a filter whose area or tag was deleted or archived is let
/// go. A chosen area or tag is marked (<see cref="IsAreaChosen"/>, <see cref="IsTagChosen"/>) so it stands out.
/// </summary>
public sealed partial class ListFiltersViewModel : ObservableObject
{
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ListFilterState filter;
    private readonly IStrings strings;
    private readonly Func<string, Brush?> areaBrush;
    private FilterChoiceViewModel? selectedArea;
    private FilterChoiceViewModel? selectedTag;
    private bool refreshing;

    [ObservableProperty]
    private IReadOnlyList<FilterChoiceViewModel> areaChoices = [];

    [ObservableProperty]
    private IReadOnlyList<FilterChoiceViewModel> tagChoices = [];

    [ObservableProperty]
    private bool hasAreas;

    [ObservableProperty]
    private bool hasTags;

    /// <summary>Whether there is anything to filter by; without areas or tags the pickers stay away.</summary>
    [ObservableProperty]
    private bool hasAny;

    [ObservableProperty]
    private bool isFiltering;

    [ObservableProperty]
    private bool isAreaChosen;

    [ObservableProperty]
    private bool isTagChosen;

    public ListFiltersViewModel(AreaList areas, TagList tags, ListFilterState filter, IStrings strings, Func<string, Brush?> areaBrush, Action<Action> runOnUi)
    {
        this.areas = areas;
        this.tags = tags;
        this.filter = filter;
        this.strings = strings;
        this.areaBrush = areaBrush;
        ClearCommand = new RelayCommand(filter.Clear);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public IRelayCommand ClearCommand { get; }

    /// <summary>The area the lists are narrowed to; the first choice, All areas, lets go.</summary>
    public FilterChoiceViewModel? SelectedArea
    {
        get => selectedArea;
        set
        {
            if (!refreshing && value is not null)
            {
                filter.SetArea(value.Id);
            }
        }
    }

    /// <summary>The tag the lists are narrowed to; the first choice, All tags, lets go.</summary>
    public FilterChoiceViewModel? SelectedTag
    {
        get => selectedTag;
        set
        {
            if (!refreshing && value is not null)
            {
                filter.SetTag(value.Id);
            }
        }
    }

    public void Refresh()
    {
        // Archived areas leave the filters, and a filter on one falls away.
        var areaList = areas.Active();
        var tagList = tags.All();
        filter.Forget([.. areaList.Select(area => area.Id)], [.. tagList.Select(tag => tag.Id)]);
        var current = filter.Current;
        refreshing = true;
        try
        {
            AreaChoices =
            [
                new FilterChoiceViewModel(null, strings.Get("Lists.AllAreas"), null),
                .. areaList.Select(area => new FilterChoiceViewModel(area.Id, area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name, areaBrush(area.ColorId))),
            ];
            TagChoices = [new FilterChoiceViewModel(null, strings.Get("Lists.AllTags"), null), .. tagList.Select(tag => new FilterChoiceViewModel(tag.Id, "#" + tag.Name, null))];
            selectedArea = AreaChoices.FirstOrDefault(choice => choice.Id == current.AreaId) ?? AreaChoices[0];
            selectedTag = TagChoices.FirstOrDefault(choice => choice.Id == current.TagId) ?? TagChoices[0];
            OnPropertyChanged(nameof(SelectedArea));
            OnPropertyChanged(nameof(SelectedTag));
        }
        finally
        {
            refreshing = false;
        }

        HasAreas = areaList.Count > 0;
        HasTags = tagList.Count > 0;
        HasAny = HasAreas || HasTags;
        IsAreaChosen = current.AreaId is not null;
        IsTagChosen = current.TagId is not null;
        IsFiltering = !current.IsEmpty;
    }
}

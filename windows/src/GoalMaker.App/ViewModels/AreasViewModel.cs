using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The areas and tags manager (M2-11): add, rename, recolor from the palette, give an emoji, reorder
/// and delete areas; add, rename and delete tags. A refused name leaves the old one and says why.
/// </summary>
public sealed partial class AreasViewModel : ObservableObject
{
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly IStrings strings;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddAreaCommand))]
    private string newAreaName = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTagCommand))]
    private string newTagName = string.Empty;

    [ObservableProperty]
    private string status = string.Empty;

    [ObservableProperty]
    private bool hasTags;

    public AreasViewModel(AreaList areas, TagList tags, IStrings strings, Func<string, Brush?> areaBrush, Action<Action> runOnUi)
    {
        this.areas = areas;
        this.tags = tags;
        this.strings = strings;
        Colors = [.. areas.Palette().Select(id => new ColorOptionViewModel(id, CultureInfo.CurrentCulture.TextInfo.ToTitleCase(id.Replace('-', ' ')), areaBrush(id)))];
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    public IReadOnlyList<ColorOptionViewModel> Colors { get; }

    public ObservableCollection<AreaRowViewModel> Areas { get; } = [];

    public ObservableCollection<TagRowViewModel> Tags { get; } = [];

    public bool HasStatus => Status.Length > 0;

    public bool HasNoTags => !HasTags;

    public void Refresh()
    {
        var areaList = areas.All();
        if (!Areas.Select(row => row.Id).SequenceEqual(areaList.Select(area => area.Id)))
        {
            Areas.Clear();
            foreach (var area in areaList)
            {
                Areas.Add(new AreaRowViewModel(area, Colors, this));
            }
        }

        for (var index = 0; index < areaList.Count; index++)
        {
            Areas[index].Update(areaList[index], index == 0, index == areaList.Count - 1);
        }

        var tagList = tags.All();
        if (!Tags.Select(row => row.Id).SequenceEqual(tagList.Select(tag => tag.Id)))
        {
            Tags.Clear();
            foreach (var tag in tagList)
            {
                Tags.Add(new TagRowViewModel(tag, this));
            }
        }

        for (var index = 0; index < tagList.Count; index++)
        {
            Tags[index].Update(tagList[index]);
        }

        HasTags = Tags.Count > 0;
    }

    internal bool RenameArea(string id, string name) => Report(areas.Rename(id, name));

    internal bool SetAreaEmoji(string id, string emoji) => areas.SetEmoji(id, emoji);

    internal bool RecolorArea(string id, string colorId) => areas.Recolor(id, colorId);

    internal void MoveArea(string id, int by)
    {
        var index = areas.All().ToList().FindIndex(area => area.Id == id);
        if (index >= 0)
        {
            areas.Move(id, index + by);
        }
    }

    internal void DeleteArea(string id) => areas.Delete(id);

    internal bool RenameTag(string id, string name) => Report(tags.Rename(id, name));

    internal void DeleteTag(string id) => tags.Delete(id);

    partial void OnStatusChanged(string value) => OnPropertyChanged(nameof(HasStatus));

    partial void OnHasTagsChanged(bool value) => OnPropertyChanged(nameof(HasNoTags));

    private bool CanAddArea() => NewAreaName.Trim().Length > 0;

    [RelayCommand(CanExecute = nameof(CanAddArea))]
    private void AddArea()
    {
        if (areas.Find(NewAreaName) is null && areas.Create(NewAreaName) is not null)
        {
            NewAreaName = string.Empty;
            Report(true);
        }
        else
        {
            Report(false);
        }
    }

    private bool CanAddTag() => NewTagName.Trim().TrimStart('#').Length > 0;

    [RelayCommand(CanExecute = nameof(CanAddTag))]
    private void AddTag()
    {
        var name = NewTagName.Trim().TrimStart('#');
        var known = tags.All().Select(tag => tag.Id).ToHashSet(StringComparer.Ordinal);
        if (tags.FindOrCreate(name) is { } id && !known.Contains(id))
        {
            NewTagName = string.Empty;
            Report(true);
        }
        else
        {
            Report(false);
        }
    }

    private bool Report(bool accepted)
    {
        Status = accepted ? string.Empty : strings.Get("Areas.NameTaken");
        return accepted;
    }
}

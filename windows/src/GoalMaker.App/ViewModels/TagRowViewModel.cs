using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One tag in the manager, renamed in place (refused when blank or taken), given to everything, an area or a project
/// (docs/lists.md, "Tags"), or deleted.
/// </summary>
public sealed class TagRowViewModel : ObservableObject
{
    private readonly AreasViewModel owner;
    private string name;
    private IReadOnlyList<TagPlaceOptionViewModel> places = [];
    private TagPlaceOptionViewModel? selectedPlace;

    public TagRowViewModel(TagItem tag, AreasViewModel owner)
    {
        this.owner = owner;
        Id = tag.Id;
        name = tag.Name;
        DeleteCommand = new RelayCommand(() => owner.DeleteTag(Id));
    }

    public string Id { get; }

    public string Name
    {
        get => name;
        set
        {
            var trimmed = value.Trim().TrimStart('#');
            if (trimmed != name && owner.RenameTag(Id, trimmed))
            {
                name = trimmed;
            }

            OnPropertyChanged();
        }
    }

    public IRelayCommand DeleteCommand { get; }

    /// <summary>What the tag can belong to: everything, each area in use and each project.</summary>
    public IReadOnlyList<TagPlaceOptionViewModel> Places
    {
        get => places;
        private set => SetProperty(ref places, value);
    }

    /// <summary>Where the tag belongs; choosing another gives it there.</summary>
    public TagPlaceOptionViewModel? SelectedPlace
    {
        get => selectedPlace;
        set
        {
            if (value is not null && value != selectedPlace && owner.PlaceTag(Id, value.Place))
            {
                selectedPlace = value;
            }

            OnPropertyChanged();
        }
    }

    /// <summary>Takes the choices and where the tag belongs now, without saving anything.</summary>
    public void ShowPlace(IReadOnlyList<TagPlaceOptionViewModel> options, TagPlace? place)
    {
        Places = options;
        selectedPlace = options.FirstOrDefault(option => option.Place == place) ?? options.FirstOrDefault();
        OnPropertyChanged(nameof(SelectedPlace));
    }

    /// <summary>Takes what the replica says now without replacing the row.</summary>
    public void Update(TagItem tag)
    {
        if (tag.Name != name)
        {
            name = tag.Name;
            OnPropertyChanged(nameof(Name));
        }
    }
}

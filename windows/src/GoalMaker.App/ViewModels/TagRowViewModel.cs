using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One tag in the manager, renamed in place (refused when blank or taken) or deleted.</summary>
public sealed class TagRowViewModel : ObservableObject
{
    private readonly AreasViewModel owner;
    private string name;

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

using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One area in the manager, edited in place: a name the list refuses when it is blank or taken, an
/// emoji, a palette color, and buttons to move, archive or delete it. The manager updates a row instead of
/// replacing it, so editing one field doesn't take the keyboard from the next.
/// </summary>
public sealed partial class AreaRowViewModel : ObservableObject
{
    private readonly AreasViewModel owner;
    private string name;
    private string emoji;
    private ColorOptionViewModel? color;

    [ObservableProperty]
    private bool canMoveUp;

    [ObservableProperty]
    private bool canMoveDown;

    public AreaRowViewModel(AreaItem area, IReadOnlyList<ColorOptionViewModel> colors, AreasViewModel owner)
    {
        this.owner = owner;
        Id = area.Id;
        Colors = colors;
        name = area.Name;
        emoji = area.Emoji ?? string.Empty;
        color = colors.FirstOrDefault(option => option.Id == area.ColorId);
        MoveUpCommand = new RelayCommand(() => owner.MoveArea(Id, -1));
        MoveDownCommand = new RelayCommand(() => owner.MoveArea(Id, 1));
        DeleteCommand = new RelayCommand(() => owner.DeleteArea(Id));
        ArchiveCommand = new RelayCommand(() => owner.ArchiveArea(Id));
    }

    public string Id { get; }

    public IReadOnlyList<ColorOptionViewModel> Colors { get; }

    public string Name
    {
        get => name;
        set
        {
            if (value.Trim() != name && owner.RenameArea(Id, value))
            {
                name = value.Trim();
            }

            OnPropertyChanged();
        }
    }

    public string Emoji
    {
        get => emoji;
        set
        {
            if (value.Trim() != emoji && owner.SetAreaEmoji(Id, value))
            {
                emoji = value.Trim();
            }

            OnPropertyChanged();
        }
    }

    public ColorOptionViewModel? Color
    {
        get => color;
        set
        {
            if (value is not null && value != color && owner.RecolorArea(Id, value.Id))
            {
                color = value;
            }

            OnPropertyChanged();
        }
    }

    public IRelayCommand MoveUpCommand { get; }

    public IRelayCommand MoveDownCommand { get; }

    public IRelayCommand DeleteCommand { get; }

    /// <summary>Hides the area from pickers and filters; the manager keeps it under Archived.</summary>
    public IRelayCommand ArchiveCommand { get; }

    /// <summary>Takes what the replica says now without replacing the row.</summary>
    public void Update(AreaItem area, bool first, bool last)
    {
        SetField(ref name, area.Name, nameof(Name));
        SetField(ref emoji, area.Emoji ?? string.Empty, nameof(Emoji));
        SetField(ref color, Colors.FirstOrDefault(option => option.Id == area.ColorId), nameof(Color));
        CanMoveUp = !first;
        CanMoveDown = !last;
    }

    private void SetField<T>(ref T field, T value, string property)
    {
        if (!EqualityComparer<T>.Default.Equals(field, value))
        {
            field = value;
            OnPropertyChanged(property);
        }
    }
}

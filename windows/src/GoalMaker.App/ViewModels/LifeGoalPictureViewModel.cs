using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One life goal picture on a card or in the editor: a picture this device keeps (by its id) or one
/// just added in the editor (its shrunk JPEG). Its image is read the first time it is shown, and is
/// null while the file is still on its way from the other device.
/// </summary>
public sealed class LifeGoalPictureViewModel
{
    private readonly Func<byte[]?> load;
    private readonly int width;
    private ImageSource? image;
    private bool loaded;

    public LifeGoalPictureViewModel(string? id, ShrunkPicture? added, string name, Func<byte[]?> load, int width, Action<LifeGoalPictureViewModel>? remove = null)
    {
        Id = id;
        Added = added;
        Name = name;
        this.load = load;
        this.width = width;
        RemoveCommand = new RelayCommand(() => remove?.Invoke(this));
    }

    /// <summary>The picture's id, or null for one added in the editor that is not saved yet.</summary>
    public string? Id { get; }

    public ShrunkPicture? Added { get; }

    /// <summary>What a screen reader says the picture is.</summary>
    public string Name { get; }

    public ImageSource? Image
    {
        get
        {
            if (!loaded)
            {
                loaded = true;
                image = PictureSource.Decode(load(), width);
            }

            return image;
        }
    }

    public bool IsWaiting => Image is null;

    public IRelayCommand RemoveCommand { get; }
}

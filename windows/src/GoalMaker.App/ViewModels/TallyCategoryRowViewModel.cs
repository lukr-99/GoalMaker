using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One of the owner's own Tally categories on the Tally page, in its palette color.</summary>
public sealed partial class TallyCategoryRowViewModel(TallyViewModel page, TallyCategory category, Brush? brush)
{
    public TallyCategory Category { get; } = category;

    public string Name => Category.Name;

    public string Emoji => Category.Emoji ?? string.Empty;

    public Brush? Brush { get; } = brush;

    [RelayCommand]
    private void Edit() => page.StartEditCategory(this);

    [RelayCommand]
    private void Delete() => page.DeleteCategory(this);
}

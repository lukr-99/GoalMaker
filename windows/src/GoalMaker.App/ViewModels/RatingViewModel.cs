using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One number of a 1 to 5 rating (mood or energy): tapping the chosen one again clears it.</summary>
public sealed partial class RatingViewModel : ObservableObject
{
    [ObservableProperty]
    private bool isChosen;

    public RatingViewModel(int value, bool chosen, Action<int> choose)
    {
        Value = value;
        isChosen = chosen;
        ChooseCommand = new RelayCommand(() => choose(value));
    }

    public int Value { get; }

    public string Label => Value.ToString(System.Globalization.CultureInfo.CurrentCulture);

    public IRelayCommand ChooseCommand { get; }
}

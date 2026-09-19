using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One step of a task's checklist, edited in place: checked, renamed (a blank name is refused), moved or deleted.</summary>
public sealed class StepRowViewModel : ObservableObject
{
    private readonly StepList steps;
    private string title;
    private bool done;
    private bool canMoveUp;
    private bool canMoveDown;

    public StepRowViewModel(StepItem step, StepList steps, Action<string, int> move)
    {
        this.steps = steps;
        Id = step.Id;
        title = step.Title;
        done = step.Done;
        MoveUpCommand = new RelayCommand(() => move(Id, -1));
        MoveDownCommand = new RelayCommand(() => move(Id, 1));
        DeleteCommand = new RelayCommand(() => steps.Delete(Id));
    }

    public string Id { get; }

    public string Title
    {
        get => title;
        set
        {
            if (value.Trim() != title && steps.Rename(Id, value))
            {
                title = value.Trim();
            }

            OnPropertyChanged();
        }
    }

    public bool Done
    {
        get => done;
        set
        {
            if (value != done && steps.SetDone(Id, value))
            {
                done = value;
            }

            OnPropertyChanged();
        }
    }

    public bool CanMoveUp
    {
        get => canMoveUp;
        private set => SetProperty(ref canMoveUp, value);
    }

    public bool CanMoveDown
    {
        get => canMoveDown;
        private set => SetProperty(ref canMoveDown, value);
    }

    public IRelayCommand MoveUpCommand { get; }

    public IRelayCommand MoveDownCommand { get; }

    public IRelayCommand DeleteCommand { get; }

    /// <summary>Takes what the replica says now without replacing the row.</summary>
    public void Update(StepItem step, bool first, bool last)
    {
        if (step.Title != title)
        {
            title = step.Title;
            OnPropertyChanged(nameof(Title));
        }

        if (step.Done != done)
        {
            done = step.Done;
            OnPropertyChanged(nameof(Done));
        }

        CanMoveUp = !first;
        CanMoveDown = !last;
    }
}

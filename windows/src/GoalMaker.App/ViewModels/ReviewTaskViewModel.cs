using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>A task still open at the end of the period: move it on, finish it, or drop it.</summary>
public sealed class ReviewTaskViewModel
{
    public ReviewTaskViewModel(TaskItem task, Action<TaskItem, string> decide)
    {
        Task = task;
        ForwardCommand = new RelayCommand(() => decide(task, "forward"));
        DoneCommand = new RelayCommand(() => decide(task, "done"));
        DropCommand = new RelayCommand(() => decide(task, "drop"));
    }

    public TaskItem Task { get; }

    public string Title => Task.Title;

    public IRelayCommand ForwardCommand { get; }

    public IRelayCommand DoneCommand { get; }

    public IRelayCommand DropCommand { get; }
}

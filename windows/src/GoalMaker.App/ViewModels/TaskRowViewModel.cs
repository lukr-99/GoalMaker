using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One task in a list: its title, a done box, and delete.</summary>
public sealed partial class TaskRowViewModel : ObservableObject
{
    private readonly TaskList tasks;

    [ObservableProperty]
    private bool isDone;

    public TaskRowViewModel(TaskItem item, TaskList tasks)
    {
        this.tasks = tasks;
        Id = item.Id;
        Title = item.Title;
        isDone = item.State == TaskState.Done;
    }

    public string Id { get; }

    public string Title { get; }

    partial void OnIsDoneChanged(bool value) => tasks.SetDone(Id, value);

    [RelayCommand]
    private void Delete() => tasks.Delete(Id);
}

using System.Collections.ObjectModel;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The composer on a list: the line, its live preview (docs/composer.md) and saving it. The list
/// supplies the day when the line names none. With the quick chat (M7) switched on, the line goes to
/// the chat instead, and quick-add is exactly as before whenever the switch is on quick-add.
/// </summary>
public sealed partial class ComposerViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Func<DateOnly, DateOnly?> defaultDay;
    private readonly Action? openPlan;
    private readonly Action<string>? openWant;
    private readonly ChatViewModel? chat;
    private ComposerDraft draft;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddTaskCommand))]
    private string newTaskTitle = string.Empty;

    [ObservableProperty]
    private bool hasChips;

    /// <param name="defaultDay">The day a line without one lands on, from the planning day (null: none).</param>
    /// <param name="openPlan">What `/plan` does (docs/plan-tomorrow.md); null where it can't run.</param>
    /// <param name="openWant">What `/want` does with its title (docs/wants.md); null where it can't run.</param>
    /// <param name="chat">The quick chat all composers share; null where the composer only adds tasks.</param>
    public ComposerViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Func<DateOnly, DateOnly?> defaultDay,
        Action<Action> runOnUi,
        Action? openPlan = null,
        Action<string>? openWant = null,
        ChatViewModel? chat = null)
    {
        this.chat = chat;
        this.openPlan = openPlan;
        this.openWant = openWant;
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.projects = projects;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.defaultDay = defaultDay;
        draft = Parse(string.Empty);
        areas.Changed += (_, _) => runOnUi(UpdatePreview);
        tags.Changed += (_, _) => runOnUi(UpdatePreview);
        if (chat is not null)
        {
            chat.PropertyChanged += (_, e) =>
            {
                if (e.PropertyName is nameof(ChatViewModel.IsChat) or nameof(ChatViewModel.IsBusy))
                {
                    OnPropertyChanged(nameof(IsChat));
                    OnPropertyChanged(nameof(Placeholder));
                    OnPropertyChanged(nameof(SendName));
                    UpdatePreview();
                }
            };
        }
    }

    /// <summary>The quick chat, for the switch and the thread above the line; null where there is none.</summary>
    public ChatViewModel? Chat => chat;

    public bool HasChat => chat is not null;

    /// <summary>Whether Enter sends the line to the chat rather than saving it as a task.</summary>
    public bool IsChat => chat?.IsChat == true;

    public string Placeholder => strings.Get(IsChat ? "Chat.Placeholder" : "Composer.Placeholder");

    /// <summary>The send button's name, which says what Enter does.</summary>
    public string SendName => strings.Get(IsChat ? "Chat.Send" : "Composer.Add");

    /// <summary>What the line will save, as it's typed.</summary>
    public ObservableCollection<ComposerChipViewModel> Chips { get; } = [];

    /// <summary>Raised after a line was saved as a task, so a quick-add box can close.</summary>
    public event EventHandler? Added;

    partial void OnNewTaskTitleChanged(string value) => UpdatePreview();

    private bool IsPlanCommand => draft.Command?.Name == PlanRules.Command && openPlan is not null;

    private bool IsWantCommand => draft.Command?.Name == WantRules.Command && openWant is not null;

    private bool CanAddTask() => chat is { IsChat: true }
        ? chat.CanSend(NewTaskTitle)
        : (draft.Title.Trim().Length > 0 && draft.Command is null) || IsPlanCommand || IsWantCommand;

    [RelayCommand(CanExecute = nameof(CanAddTask))]
    private async Task AddTaskAsync()
    {
        if (chat is { IsChat: true })
        {
            // The line leaves the box as it goes, and comes back if it couldn't.
            var line = NewTaskTitle;
            NewTaskTitle = string.Empty;
            if (!await chat.SendAsync(line) && NewTaskTitle.Length == 0)
            {
                NewTaskTitle = line;
            }

            return;
        }

        if (IsPlanCommand)
        {
            NewTaskTitle = string.Empty;
            openPlan?.Invoke();
            return;
        }

        if (IsWantCommand)
        {
            var title = draft.Command!.Argument;
            NewTaskTitle = string.Empty;
            openWant?.Invoke(title);
            return;
        }

        var placed = draft.PlannedDate is null && defaultDay(Today()) is { } day ? draft with { PlannedDate = day } : draft;
        if (tasks.Add(placed) is not null)
        {
            NewTaskTitle = string.Empty;
            Added?.Invoke(this, EventArgs.Empty);
        }
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    private ComposerDraft Parse(string line) => ComposerParser.Parse(line, time.GetLocalNow().DateTime, settings.DayStartHour);

    private void UpdatePreview()
    {
        draft = Parse(NewTaskTitle);
        Chips.Clear();
        foreach (var chip in ComposerChips.Build(NewTaskTitle, draft, Today(), areas.All(), tags.Names(), projects.All(), strings, areaBrush, Remove))
        {
            Chips.Add(chip);
        }

        // A chat message is plain words, so it shows no preview of what a task would be.
        HasChips = Chips.Count > 0 && !IsChat;
        AddTaskCommand.NotifyCanExecuteChanged();
    }

    private void Remove(ComposerChipViewModel chip) => NewTaskTitle = ComposerChips.RemoveParts(NewTaskTitle, chip.Spans);
}

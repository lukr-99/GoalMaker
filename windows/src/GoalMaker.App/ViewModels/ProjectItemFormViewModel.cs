using System.Windows.Input;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The new item window of the Projects page (docs/projects.md): the title, the type, the column (it
/// follows the type until one is picked), the priority, a milestone of the project, the planned day,
/// the deadline and notes. Enter in the title adds the item, Ctrl+Enter adds it from anywhere, the notes
/// included, and Escape closes the window. With Add another on, the window stays open after an item
/// is added, empty again but for the choices, so several go in a row.
/// </summary>
public sealed partial class ProjectItemFormViewModel : ObservableObject
{
    private readonly IStrings strings;
    private readonly Func<ProjectItemDraft, bool> add;

    // The column follows the type, an idea starting in the backlog, until one is picked.
    private bool columnPicked;
    private bool columnFollowing;

    [ObservableProperty]
    private string title;

    [ObservableProperty]
    private bool titleMissing;

    [ObservableProperty]
    private string notes = string.Empty;

    [ObservableProperty]
    private string itemType;

    [ObservableProperty]
    private string column;

    [ObservableProperty]
    private string priority = ProjectRules.Normal;

    [ObservableProperty]
    private ChoiceViewModel milestone;

    [ObservableProperty]
    private DateTime? plannedDay;

    [ObservableProperty]
    private DateTime? deadline;

    [ObservableProperty]
    private bool addAnother;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasAdded))]
    private string addedText = string.Empty;

    /// <param name="strings">The copy.</param>
    /// <param name="projectName">The project the item goes into, for the heading.</param>
    /// <param name="choices">The pickers' lines, the board's own.</param>
    /// <param name="start">What the form opens with: the title and type the quick line held, and a column when it was opened from one.</param>
    /// <param name="addAnother">Whether the window stays open after an item is added.</param>
    /// <param name="add">Adds an item to the project; false when nothing was added.</param>
    public ProjectItemFormViewModel(
        IStrings strings, string projectName, ProjectItemChoices choices, ProjectItemStart start, bool addAnother, Func<ProjectItemDraft, bool> add)
    {
        this.strings = strings;
        this.add = add;
        Heading = strings.Get("ProjectItem.Heading", projectName);
        ItemTypes = choices.ItemTypes;
        Columns = choices.Columns;
        Priorities = choices.Priorities;
        Milestones = [new ChoiceViewModel(null, strings.Get("ProjectItem.NoMilestone")), .. choices.Milestones];
        title = start.Title;
        itemType = start.ItemType;
        column = start.Column ?? ProjectRules.ColumnFor(start.ItemType);
        columnPicked = start.Column is not null;
        milestone = Milestones[0];
        this.addAnother = addAnother;
    }

    /// <summary>The window has finished: closed with Escape or Cancel, or an item went in and Add another was off.</summary>
    public event EventHandler? Finished;

    /// <summary>The keyboard belongs back in the title: the form was emptied for the next item, or the title was missing.</summary>
    public event EventHandler? TitleWanted;

    /// <summary>What the window's title bar says: the project the item goes into.</summary>
    public string Heading { get; }

    public IReadOnlyList<ChoiceViewModel> ItemTypes { get; }

    /// <summary>The columns a new item can start in; Done is not one of them.</summary>
    public IReadOnlyList<ChoiceViewModel> Columns { get; }

    public IReadOnlyList<ChoiceViewModel> Priorities { get; }

    /// <summary>No milestone, then the project's own.</summary>
    public IReadOnlyList<ChoiceViewModel> Milestones { get; }

    /// <summary>Whether the project has milestones, so the picker is worth showing.</summary>
    public bool HasMilestones => Milestones.Count > 1;

    /// <summary>Whether an item went in while the window stayed open, so the line saying so shows.</summary>
    public bool HasAdded => AddedText.Length > 0;

    /// <summary>How many items went in from this window.</summary>
    public int Added { get; private set; }

    /// <summary>
    /// A key the window heard: Enter in the title, or Ctrl+Enter anywhere, adds the item, and Escape
    /// closes the window. Any other Enter (a new line in the notes, a button) is left alone. True when
    /// the key was used.
    /// </summary>
    public bool Press(Key key, ModifierKeys modifiers, bool inTitle)
    {
        switch (key)
        {
            case Key.Enter when modifiers == ModifierKeys.Control || (modifiers == ModifierKeys.None && inTitle):
                Create();
                return true;
            case Key.Escape when modifiers == ModifierKeys.None:
                Cancel();
                return true;
            default:
                return false;
        }
    }

    /// <summary>Adds the item; without a title it says one is needed and adds nothing.</summary>
    [RelayCommand]
    public void Create()
    {
        var name = Title.Trim();
        if (name.Length == 0)
        {
            TitleMissing = true;
            TitleWanted?.Invoke(this, EventArgs.Empty);
            return;
        }

        var draft = new ProjectItemDraft(name, ItemType, Column, Priority)
        {
            Notes = Notes.Trim(),
            MilestoneId = Milestone.Id,
            PlannedDay = PlannedDay is { } day ? DateOnly.FromDateTime(day) : null,
            Deadline = Deadline is { } due ? DateOnly.FromDateTime(due) : null,
        };
        if (!add(draft))
        {
            return;
        }

        Added++;
        if (!AddAnother)
        {
            Finished?.Invoke(this, EventArgs.Empty);
            return;
        }

        // The next item keeps the choices, so a run of bugs for one milestone needs them set once.
        AddedText = strings.Get("ProjectItem.Added", name);
        Title = string.Empty;
        Notes = string.Empty;
        TitleWanted?.Invoke(this, EventArgs.Empty);
    }

    [RelayCommand]
    public void Cancel() => Finished?.Invoke(this, EventArgs.Empty);

    partial void OnTitleChanged(string value) => TitleMissing &= value.Trim().Length == 0;

    partial void OnItemTypeChanged(string value)
    {
        if (columnPicked)
        {
            return;
        }

        columnFollowing = true;
        Column = ProjectRules.ColumnFor(value);
        columnFollowing = false;
    }

    partial void OnColumnChanged(string value) => columnPicked |= !columnFollowing;
}

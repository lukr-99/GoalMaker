using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The bottom bar of a page (docs/composer.md, "The bottom bar on every list"): the line, its live
/// preview, the switch to the quick chat, and one round button. While the line is empty the button is
/// a plus that opens the page's full form; once something is typed it is the send arrow that adds what
/// the line says. In the quick chat it is always the send arrow. A bar without a form (the quick-add
/// box, Plan tomorrow) only ever sends.
/// </summary>
public abstract partial class BarViewModel : ObservableObject
{
    private readonly ChatViewModel? chat;
    private bool hasForm;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowsPlus), nameof(ShowsSend), nameof(ButtonName))]
    [NotifyCanExecuteChangedFor(nameof(SendCommand), nameof(PressCommand))]
    private string line = string.Empty;

    [ObservableProperty]
    private bool hasChips;

    protected BarViewModel(IStrings strings, ChatViewModel? chat)
    {
        Strings = strings;
        this.chat = chat;
        if (chat is not null)
        {
            chat.PropertyChanged += (_, e) =>
            {
                if (e.PropertyName is nameof(ChatViewModel.IsChat) or nameof(ChatViewModel.IsBusy))
                {
                    OnPropertyChanged(nameof(IsChat));
                    OnPropertyChanged(nameof(Placeholder));
                    OnPropertyChanged(nameof(SendName));
                    OnPropertyChanged(nameof(ShowsPlus));
                    OnPropertyChanged(nameof(ShowsSend));
                    OnPropertyChanged(nameof(ButtonName));
                    RefreshPreview();
                }
            };
        }
    }

    /// <summary>The quick chat, for the switch and the thread above the line; null where there is none.</summary>
    public ChatViewModel? Chat => chat;

    public bool HasChat => chat is not null;

    /// <summary>Whether the line goes to the chat rather than adding an item.</summary>
    public bool IsChat => chat?.IsChat == true;

    /// <summary>Whether the plus can open a full form here.</summary>
    public bool HasForm
    {
        get => hasForm;
        protected set
        {
            if (SetProperty(ref hasForm, value))
            {
                OnPropertyChanged(nameof(ShowsPlus));
                OnPropertyChanged(nameof(ShowsSend));
                OnPropertyChanged(nameof(ButtonName));
                OpenFormCommand.NotifyCanExecuteChanged();
                PressCommand.NotifyCanExecuteChanged();
            }
        }
    }

    public string Placeholder => IsChat ? Strings.Get("Chat.Placeholder") : ItemPlaceholder;

    /// <summary>What sending does, as the send arrow's name.</summary>
    public string SendName => IsChat ? Strings.Get("Chat.Send") : AddName;

    /// <summary>What the plus opens, as its name ("New want").</summary>
    public abstract string FormName { get; }

    /// <summary>True while the button is the plus: a form to open, an empty line and no chat.</summary>
    public bool ShowsPlus => HasForm && !IsChat && Line.Trim().Length == 0;

    public bool ShowsSend => !ShowsPlus;

    /// <summary>The button's accessible name and tooltip, for whichever it is right now.</summary>
    public string ButtonName => ShowsPlus ? FormName : SendName;

    /// <summary>What the line will save, as it's typed.</summary>
    public ObservableCollection<ComposerChipViewModel> Chips { get; } = [];

    protected IStrings Strings { get; }

    protected abstract string ItemPlaceholder { get; }

    protected abstract string AddName { get; }

    /// <summary>Whether the line as it stands can be sent (the chat aside).</summary>
    protected abstract bool CanAdd();

    /// <summary>Adds what the line says, or hands it to the form; true when the line is done with.</summary>
    protected abstract bool Add();

    /// <summary>Opens the full form, filled in with what <paramref name="text"/> says (empty for a blank form).</summary>
    protected abstract void OpenFormWith(string text);

    /// <summary>The preview chips for the line, in the order the item reads.</summary>
    protected abstract IEnumerable<ComposerChipViewModel> BuildChips();

    /// <summary>Called when the line changes, before the preview is built again.</summary>
    protected virtual void OnLineEdited()
    {
    }

    /// <summary>Builds the preview again, for a change outside the line (an area renamed, chat switched).</summary>
    protected void RefreshPreview()
    {
        Chips.Clear();
        foreach (var chip in BuildChips())
        {
            Chips.Add(chip);
        }

        // A chat message is plain words, so it shows no preview of what an item would be.
        HasChips = Chips.Count > 0 && !IsChat;
        SendCommand.NotifyCanExecuteChanged();
        PressCommand.NotifyCanExecuteChanged();
    }

    partial void OnLineChanged(string value)
    {
        OnLineEdited();
        RefreshPreview();
    }

    private bool CanSend() => chat is { IsChat: true } ? chat.CanSend(Line) : CanAdd();

    /// <summary>Enter: adds the line, or sends it to the chat.</summary>
    [RelayCommand(CanExecute = nameof(CanSend))]
    private async Task SendAsync()
    {
        if (chat is { IsChat: true })
        {
            // The line leaves the box as it goes, and comes back if it couldn't.
            var sent = Line;
            Line = string.Empty;
            if (!await chat.SendAsync(sent) && Line.Length == 0)
            {
                Line = sent;
            }

            return;
        }

        if (Add())
        {
            Line = string.Empty;
        }
    }

    private bool CanPress() => ShowsPlus || CanSend();

    /// <summary>The round button: the plus opens the form, the arrow sends.</summary>
    [RelayCommand(CanExecute = nameof(CanPress))]
    private Task PressAsync()
    {
        if (ShowsPlus)
        {
            OpenFormWith(string.Empty);
            return Task.CompletedTask;
        }

        return SendAsync();
    }

    /// <summary>Ctrl+N: the full form, filled in with what the line says so far.</summary>
    [RelayCommand(CanExecute = nameof(HasForm))]
    private void OpenForm() => OpenFormWith(IsChat ? string.Empty : Line.Trim());

    /// <summary>Esc: an empty line.</summary>
    [RelayCommand]
    private void Clear() => Line = string.Empty;

    /// <summary>For a form opened with the line: once it saves, the line is done with.</summary>
    protected void ClearIf(string text)
    {
        if (Line.Trim() == text)
        {
            Line = string.Empty;
        }
    }
}

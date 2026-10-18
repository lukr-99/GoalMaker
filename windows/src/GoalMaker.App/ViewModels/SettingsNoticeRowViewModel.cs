using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Notices;

namespace GoalMaker.App.ViewModels;

/// <summary>One line of the banner at the top of Settings: what needs the owner, what to do, a button and Dismiss.</summary>
public sealed partial class SettingsNoticeRowViewModel
{
    private readonly Action<Notice> act;
    private readonly Action<Notice> dismiss;

    public SettingsNoticeRowViewModel(Notice notice, IStrings strings, Action<Notice> act, Action<Notice> dismiss)
    {
        Notice = notice;
        this.act = act;
        this.dismiss = dismiss;
        Title = NoticeCopy.Title(notice, strings);
        Advice = NoticeCopy.Advice(notice, strings);
        ActionText = NoticeCopy.Action(notice, strings);
        DismissName = strings.Get("Notice.DismissName", Title);
    }

    public Notice Notice { get; }

    public string Title { get; }

    public string Advice { get; }

    public string ActionText { get; }

    /// <summary>The Dismiss button's accessible name, which says what it dismisses.</summary>
    public string DismissName { get; }

    /// <summary>An update gets the download icon; a problem gets the warning icon.</summary>
    public bool IsUpdate => Notice is Notice.UpdateReady;

    [RelayCommand]
    private void Act() => act(Notice);

    [RelayCommand]
    private void Dismiss() => dismiss(Notice);
}

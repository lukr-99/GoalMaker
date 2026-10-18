using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.App.Localization;
using GoalMaker.Core.Notices;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The banner at the top of Settings (docs/problems.md, "What the owner sees"): each thing that needs
/// the owner in plain words, with a button that deals with it and Dismiss. With nothing to act on the
/// banner is not there. The page decides what a button does (<see cref="ActRequested"/>).
/// </summary>
public sealed partial class SettingsNoticesViewModel : ObservableObject
{
    private readonly NoticeBoard board;
    private readonly IStrings strings;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNotices))]
    private IReadOnlyList<SettingsNoticeRowViewModel> rows = [];

    public SettingsNoticesViewModel(NoticeBoard board, IStrings strings, Action<Action> runOnUi)
    {
        this.board = board;
        this.strings = strings;
        board.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>A line's button was pressed: install the update, or jump to the section that helps.</summary>
    public event EventHandler<Notice>? ActRequested;

    public bool HasNotices => Rows.Count > 0;

    private void Refresh() =>
        Rows = [.. board.Current.Select(notice => new SettingsNoticeRowViewModel(notice, strings, Act, board.Dismiss))];

    private void Act(Notice notice) => ActRequested?.Invoke(this, notice);
}

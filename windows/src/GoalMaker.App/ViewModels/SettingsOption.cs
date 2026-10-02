namespace GoalMaker.App.ViewModels;

/// <summary>
/// One option of a segmented or dropdown row in Settings: the value it stands for and the words it
/// shows. The text is also what the page's search matches.
/// </summary>
public sealed record SettingsOption(object Value, string Text)
{
    public override string ToString() => Text;
}

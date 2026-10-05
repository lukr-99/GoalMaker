using System.Windows;
using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The piece of an event's bar one week row draws (docs/calendar.md, the 'bars' rule): from
/// <see cref="Column"/> across <see cref="Span"/> days on its lane, square where the event goes on into
/// the row before or after, in its area's colour or the accent. Enter or a click opens the editor.
/// </summary>
public sealed class CalendarBarViewModel(
    string id,
    string title,
    int column,
    int span,
    int lane,
    bool before,
    bool after,
    Brush? brush,
    string automationName,
    Action open)
{
    /// <summary>How tall a lane is, the bar and the gap under it.</summary>
    public const double LaneHeight = 18;

    public string Id { get; } = id;

    public string Title { get; } = title;

    /// <summary>The first column, 0 for Monday.</summary>
    public int Column { get; } = column;

    /// <summary>How many columns it covers.</summary>
    public int Span { get; } = span;

    public int Lane { get; } = lane;

    public bool Before { get; } = before;

    public bool After { get; } = after;

    /// <summary>Whether the event's first day is in this row, where the bar wears its colour in full.</summary>
    public bool StartsHere => !Before;

    /// <summary>The area's colour, or null for the accent.</summary>
    public Brush? Brush { get; } = brush;

    /// <summary>"Prague, 12 to 15 October".</summary>
    public string AutomationName { get; } = automationName;

    /// <summary>Its place in the row: down by its lane, and in from a day's edge where the event starts or ends there.</summary>
    public Thickness Margin { get; } = new(before ? 0 : 3, lane * LaneHeight, after ? 0 : 3, 0);

    /// <summary>Round where the event starts or ends, square where it goes on.</summary>
    public CornerRadius Corners { get; } = new(before ? 0 : 4, after ? 0 : 4, after ? 0 : 4, before ? 0 : 4);

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);
}

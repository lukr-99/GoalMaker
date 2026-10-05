namespace GoalMaker.App.ViewModels;

/// <summary>
/// One week row of the calendar grid: its seven days, and the event bars drawn across them under each
/// day's number. The row is as tall as its bars need, and never less than a day without any.
/// </summary>
public sealed class CalendarWeekViewModel
{
    /// <summary>How many lanes of bars a row draws; the events past them show as "+N" under their days.</summary>
    public const int Lanes = 3;

    /// <summary>Where the bars start, under the day's number and its task bar.</summary>
    public const double BarsTop = 36;

    private const double PlainHeight = 64;
    private const double MoreHeight = 16;
    private const double Bottom = 6;

    public CalendarWeekViewModel(
        IReadOnlyList<CalendarCellViewModel> cells,
        IReadOnlyList<CalendarBarViewModel> bars,
        IReadOnlyList<CalendarMoreViewModel> more)
    {
        Cells = cells;
        Bars = bars;
        More = more;
        var lanes = bars.Count == 0 ? 0 : bars.Max(bar => bar.Lane) + 1;
        var needed = BarsTop + (lanes * CalendarBarViewModel.LaneHeight) + (more.Count > 0 ? MoreHeight : 0) + Bottom;
        CellHeight = HasBars ? Math.Max(PlainHeight, needed) : PlainHeight;
    }

    public IReadOnlyList<CalendarCellViewModel> Cells { get; }

    /// <summary>The bars on the first three lanes, by lane and then by column, the order Tab takes them in.</summary>
    public IReadOnlyList<CalendarBarViewModel> Bars { get; }

    public IReadOnlyList<CalendarMoreViewModel> More { get; }

    public bool HasBars => Bars.Count > 0 || More.Count > 0;

    /// <summary>How tall each day of the row is.</summary>
    public double CellHeight { get; }

    /// <summary>The day's face inside its one-pixel frame.</summary>
    public double FaceHeight => CellHeight - 2;
}

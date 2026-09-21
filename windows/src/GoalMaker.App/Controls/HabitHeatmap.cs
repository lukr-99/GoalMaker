using System.Collections;
using System.Windows;
using System.Windows.Media;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Controls;

/// <summary>
/// A habit's heatmap (spec, story 42): a column per week, Monday at the top, each day in the accent as
/// strong as its share of the target. A paused day is an outline, a skipped one a dash, a day over a
/// limit is solid danger, and a day that isn't due stays empty. It shows the last weeks that fit the
/// width.
/// </summary>
public sealed class HabitHeatmap : FrameworkElement
{
    public static readonly DependencyProperty HeatProperty = DependencyProperty.Register(
        nameof(Heat), typeof(IEnumerable), typeof(HabitHeatmap), new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty AccentBrushProperty = DependencyProperty.Register(
        nameof(AccentBrush), typeof(Brush), typeof(HabitHeatmap), new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty OutlineBrushProperty = DependencyProperty.Register(
        nameof(OutlineBrush), typeof(Brush), typeof(HabitHeatmap), new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty MutedBrushProperty = DependencyProperty.Register(
        nameof(MutedBrush), typeof(Brush), typeof(HabitHeatmap), new FrameworkPropertyMetadata(Brushes.DimGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty OverBrushProperty = DependencyProperty.Register(
        nameof(OverBrush), typeof(Brush), typeof(HabitHeatmap), new FrameworkPropertyMetadata(Brushes.IndianRed, FrameworkPropertyMetadataOptions.AffectsRender));

    private const double Cell = 12;
    private const double Gap = 3;

    public HabitHeatmap()
    {
        SetResourceReference(AccentBrushProperty, "GM.AccentBrush");
        SetResourceReference(OutlineBrushProperty, "GM.OutlineBrush");
        SetResourceReference(MutedBrushProperty, "GM.TextMutedBrush");
        SetResourceReference(OverBrushProperty, "GM.DangerBrush");
    }

    /// <summary>The days from a Monday to today, as <see cref="HabitRules.Heat"/> reads them.</summary>
    public IEnumerable? Heat
    {
        get => (IEnumerable?)GetValue(HeatProperty);
        set => SetValue(HeatProperty, value);
    }

    public Brush AccentBrush
    {
        get => (Brush)GetValue(AccentBrushProperty);
        set => SetValue(AccentBrushProperty, value);
    }

    public Brush OutlineBrush
    {
        get => (Brush)GetValue(OutlineBrushProperty);
        set => SetValue(OutlineBrushProperty, value);
    }

    public Brush MutedBrush
    {
        get => (Brush)GetValue(MutedBrushProperty);
        set => SetValue(MutedBrushProperty, value);
    }

    /// <summary>A day that went over a limit habit's number.</summary>
    public Brush OverBrush
    {
        get => (Brush)GetValue(OverBrushProperty);
        set => SetValue(OverBrushProperty, value);
    }

    protected override Size MeasureOverride(Size availableSize)
    {
        var weeks = Weeks(availableSize.Width);
        return new Size(weeks <= 0 ? 0 : (weeks * (Cell + Gap)) - Gap, (7 * (Cell + Gap)) - Gap);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        var days = Days();
        var weeks = Weeks(ActualWidth);
        if (days.Count == 0 || weeks <= 0)
        {
            return;
        }

        var shown = days.Skip(Math.Max(0, ((days.Count + 6) / 7) - weeks) * 7).ToList();
        var track = Faint(OutlineBrush, 0.2);
        var empty = Faint(OutlineBrush, 0.07);
        var paused = new Pen(Faint(MutedBrush, 0.7), Cell / 6);
        var skipped = new Pen(MutedBrush, Cell / 6);
        for (var index = 0; index < shown.Count; index++)
        {
            var left = index / 7 * (Cell + Gap);
            var top = index % 7 * (Cell + Gap);
            var box = new Rect(left, top, Cell, Cell);
            switch (shown[index].Kind)
            {
                case HabitHeatKind.None:
                    // A day before the start or off duty: a hint of the grid, nothing more.
                    drawingContext.DrawRoundedRectangle(empty, null, box, Cell / 4, Cell / 4);
                    break;
                case HabitHeatKind.Paused:
                    drawingContext.DrawRoundedRectangle(null, paused, box, Cell / 4, Cell / 4);
                    break;
                case HabitHeatKind.Over:
                    drawingContext.DrawRoundedRectangle(OverBrush, null, box, Cell / 4, Cell / 4);
                    break;
                case HabitHeatKind.Skipped:
                    drawingContext.DrawRoundedRectangle(track, null, box, Cell / 4, Cell / 4);
                    drawingContext.DrawLine(skipped, new Point(left + (Cell * 0.25), top + (Cell / 2)), new Point(left + (Cell * 0.75), top + (Cell / 2)));
                    break;
                default:
                    var fraction = shown[index].Fraction;
                    drawingContext.DrawRoundedRectangle(
                        fraction <= 0 ? track : Faint(AccentBrush, 0.3 + (0.7 * fraction)),
                        null,
                        box,
                        Cell / 4,
                        Cell / 4);
                    break;
            }
        }

        // Today, outlined in the accent.
        var last = shown.Count - 1;
        drawingContext.DrawRoundedRectangle(
            null,
            new Pen(AccentBrush, Gap * 0.75),
            new Rect((last / 7 * (Cell + Gap)) - (Gap / 2), (last % 7 * (Cell + Gap)) - (Gap / 2), Cell + Gap, Cell + Gap),
            Cell / 3,
            Cell / 3);
    }

    // A frozen copy at the wanted opacity; the theme's own brush is shared, so it is never changed.
    private static Brush Faint(Brush brush, double opacity)
    {
        var copy = brush.CloneCurrentValue();
        copy.Opacity = Math.Clamp(opacity, 0, 1);
        copy.Freeze();
        return copy;
    }

    private List<HabitHeat> Days() => Heat is null ? [] : [.. Heat.OfType<HabitHeat>()];

    private int Weeks(double width)
    {
        var all = (Days().Count + 6) / 7;
        if (all == 0 || double.IsInfinity(width))
        {
            return all;
        }

        return Math.Clamp((int)((width + Gap) / (Cell + Gap)), 0, all);
    }
}

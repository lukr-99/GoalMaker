using System.Windows;
using System.Windows.Media;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Controls;

/// <summary>
/// Mood and energy over the past reviews (docs/stats.md, spec story 64): two lines on the 1 to 5 the
/// review asked for, with a gap wherever a review rated only one of them.
/// </summary>
public sealed class RatingLines : FrameworkElement
{
    public static readonly DependencyProperty RatingsProperty = DependencyProperty.Register(
        nameof(Ratings),
        typeof(IReadOnlyList<StatsDigest.Rating>),
        typeof(RatingLines),
        new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty MoodBrushProperty = DependencyProperty.Register(
        nameof(MoodBrush),
        typeof(Brush),
        typeof(RatingLines),
        new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender, OnBrushChanged));

    public static readonly DependencyProperty EnergyBrushProperty = DependencyProperty.Register(
        nameof(EnergyBrush),
        typeof(Brush),
        typeof(RatingLines),
        new FrameworkPropertyMetadata(Brushes.DimGray, FrameworkPropertyMetadataOptions.AffectsRender, OnBrushChanged));

    public static readonly DependencyProperty FallbackBrushProperty = DependencyProperty.Register(
        nameof(FallbackBrush),
        typeof(Brush),
        typeof(RatingLines),
        new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender, OnBrushChanged));

    public static readonly DependencyProperty EnergyLineBrushProperty = DependencyProperty.Register(
        nameof(EnergyLineBrush), typeof(Brush), typeof(RatingLines), new FrameworkPropertyMetadata(Brushes.DimGray));

    public static readonly DependencyProperty GridBrushProperty = DependencyProperty.Register(
        nameof(GridBrush), typeof(Brush), typeof(RatingLines), new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public RatingLines()
    {
        SetResourceReference(MoodBrushProperty, "GM.AccentBrush");
        SetResourceReference(EnergyBrushProperty, "GM.PrimaryBrush");
        SetResourceReference(FallbackBrushProperty, "GM.TextMutedBrush");
        SetResourceReference(GridBrushProperty, "GM.OutlineBrush");
        Chosen();
    }

    /// <summary>The reviews that rated anything, oldest first.</summary>
    public IReadOnlyList<StatsDigest.Rating>? Ratings
    {
        get => (IReadOnlyList<StatsDigest.Rating>?)GetValue(RatingsProperty);
        set => SetValue(RatingsProperty, value);
    }

    public Brush MoodBrush
    {
        get => (Brush)GetValue(MoodBrushProperty);
        set => SetValue(MoodBrushProperty, value);
    }

    public Brush EnergyBrush
    {
        get => (Brush)GetValue(EnergyBrushProperty);
        set => SetValue(EnergyBrushProperty, value);
    }

    /// <summary>What the energy line takes when the theme's primary is the accent as well (Track).</summary>
    public Brush FallbackBrush
    {
        get => (Brush)GetValue(FallbackBrushProperty);
        set => SetValue(FallbackBrushProperty, value);
    }

    /// <summary>The colour the energy line ends up in, which the legend beside the chart follows.</summary>
    public Brush EnergyLineBrush
    {
        get => (Brush)GetValue(EnergyLineBrushProperty);
        private set => SetValue(EnergyLineBrushProperty, value);
    }

    public Brush GridBrush
    {
        get => (Brush)GetValue(GridBrushProperty);
        set => SetValue(GridBrushProperty, value);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        var ratings = Ratings;
        if (ratings is null || ratings.Count == 0 || ActualWidth <= 0 || ActualHeight <= 0)
        {
            return;
        }

        const double Margin = 8;
        var top = Margin;
        var bottom = Math.Max(ActualHeight - Margin, Margin + 1);
        var step = ratings.Count > 1 ? ActualWidth / (ratings.Count - 1) : 0;

        Point At(int index, int value)
        {
            var x = ratings.Count > 1 ? index * step : ActualWidth / 2;
            var y = bottom - ((Math.Clamp(value, 1, 5) - 1) / 4d * (bottom - top));
            return new Point(x, y);
        }

        var grid = new Pen(Faint(GridBrush), 1);
        grid.Freeze();
        foreach (var line in new[] { 1, 3, 5 })
        {
            var y = At(0, line).Y;
            drawingContext.DrawLine(grid, new Point(0, y), new Point(ActualWidth, y));
        }

        Draw(drawingContext, ratings.Select(rating => rating.Mood).ToList(), MoodBrush, At);
        Draw(drawingContext, ratings.Select(rating => rating.Energy).ToList(), EnergyLineBrush, At);
    }

    private static void Draw(DrawingContext drawingContext, IReadOnlyList<int?> values, Brush brush, Func<int, int, Point> at)
    {
        var pen = new Pen(brush, 2.5);
        pen.Freeze();
        Point? last = null;
        for (var index = 0; index < values.Count; index++)
        {
            if (values[index] is not { } value)
            {
                last = null;
                continue;
            }

            var here = at(index, value);
            if (last is { } from)
            {
                drawingContext.DrawLine(pen, from, here);
            }

            drawingContext.DrawEllipse(brush, null, here, 3.5, 3.5);
            last = here;
        }
    }

    private static void OnBrushChanged(DependencyObject control, DependencyPropertyChangedEventArgs args) =>
        ((RatingLines)control).Chosen();

    // The two lines must not be the same colour: Track paints its accent and its primary the same lime.
    private void Chosen() =>
        EnergyLineBrush = MoodBrush is SolidColorBrush mood && EnergyBrush is SolidColorBrush energy && mood.Color == energy.Color
            ? FallbackBrush
            : EnergyBrush;

    // A frozen copy at a low opacity; the theme's own brush is shared, so it is never changed.
    private static Brush Faint(Brush brush)
    {
        var copy = brush.CloneCurrentValue();
        copy.Opacity = 0.3;
        copy.Freeze();
        return copy;
    }
}

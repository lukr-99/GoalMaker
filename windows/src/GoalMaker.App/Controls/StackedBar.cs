using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace GoalMaker.App.Controls;

/// <summary>
/// A bar made of parts laid end to end, each in its own brush and as long as its share of the whole
/// (docs/tally.md): flat for today's time by category, upright for a day or a week in a chart. It
/// grows in over the standard duration when it first shows; with reduce motion it fades in instead.
/// An empty bar leaves a faint stub, so a chart keeps its baseline, as <see cref="ShareBar"/> does.
/// </summary>
public sealed class StackedBar : FrameworkElement
{
    public static readonly DependencyProperty PartsProperty = DependencyProperty.Register(
        nameof(Parts),
        typeof(IReadOnlyList<(double Amount, Brush? Brush)>),
        typeof(StackedBar),
        new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty FractionProperty = DependencyProperty.Register(
        nameof(Fraction), typeof(double), typeof(StackedBar), new FrameworkPropertyMetadata(1d, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty FlatProperty = DependencyProperty.Register(
        nameof(Flat), typeof(bool), typeof(StackedBar), new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty GrowthProperty = DependencyProperty.Register(
        nameof(Growth), typeof(double), typeof(StackedBar), new FrameworkPropertyMetadata(1d, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(StackedBar), new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty FallbackBrushProperty = DependencyProperty.Register(
        nameof(FallbackBrush), typeof(Brush), typeof(StackedBar), new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty ReduceMotionProperty = DependencyProperty.Register(
        nameof(ReduceMotion), typeof(bool), typeof(StackedBar), new PropertyMetadata(false));

    public static readonly DependencyProperty GrowDurationProperty = DependencyProperty.Register(
        nameof(GrowDuration), typeof(Duration), typeof(StackedBar), new PropertyMetadata(new Duration(TimeSpan.FromMilliseconds(250))));

    public static readonly DependencyProperty FadeDurationProperty = DependencyProperty.Register(
        nameof(FadeDuration), typeof(Duration), typeof(StackedBar), new PropertyMetadata(new Duration(TimeSpan.FromMilliseconds(120))));

    public StackedBar()
    {
        SetResourceReference(TrackBrushProperty, "GM.OutlineBrush");
        SetResourceReference(FallbackBrushProperty, "GM.AccentBrush");
        SetResourceReference(ReduceMotionProperty, "GM.ReduceMotion");
        SetResourceReference(GrowDurationProperty, "GM.StandardDuration");
        SetResourceReference(FadeDurationProperty, "GM.QuickDuration");
        Loaded += (_, _) => GrowIn();
    }

    /// <summary>The parts in the order they are laid, from the left or from the bottom, each with its amount.</summary>
    public IReadOnlyList<(double Amount, Brush? Brush)>? Parts
    {
        get => (IReadOnlyList<(double Amount, Brush? Brush)>?)GetValue(PartsProperty);
        set => SetValue(PartsProperty, value);
    }

    /// <summary>How much of the length the whole bar fills, 0 to 1: its share of the biggest bar in its chart.</summary>
    public double Fraction
    {
        get => (double)GetValue(FractionProperty);
        set => SetValue(FractionProperty, value);
    }

    /// <summary>Flat lays the parts from the left, upright from the bottom.</summary>
    public bool Flat
    {
        get => (bool)GetValue(FlatProperty);
        set => SetValue(FlatProperty, value);
    }

    /// <summary>How far the bar has grown in, 0 to 1; animated when it first shows.</summary>
    public double Growth
    {
        get => (double)GetValue(GrowthProperty);
        set => SetValue(GrowthProperty, value);
    }

    public Brush TrackBrush
    {
        get => (Brush)GetValue(TrackBrushProperty);
        set => SetValue(TrackBrushProperty, value);
    }

    /// <summary>For a part that has no brush of its own.</summary>
    public Brush FallbackBrush
    {
        get => (Brush)GetValue(FallbackBrushProperty);
        set => SetValue(FallbackBrushProperty, value);
    }

    public bool ReduceMotion
    {
        get => (bool)GetValue(ReduceMotionProperty);
        set => SetValue(ReduceMotionProperty, value);
    }

    public Duration GrowDuration
    {
        get => (Duration)GetValue(GrowDurationProperty);
        set => SetValue(GrowDurationProperty, value);
    }

    public Duration FadeDuration
    {
        get => (Duration)GetValue(FadeDurationProperty);
        set => SetValue(FadeDurationProperty, value);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        if (ActualWidth <= 0 || ActualHeight <= 0)
        {
            return;
        }

        var parts = (Parts ?? []).Where(part => double.IsFinite(part.Amount) && part.Amount > 0).ToList();
        var total = parts.Sum(part => part.Amount);
        var full = Flat ? ActualWidth : ActualHeight;
        var radius = Flat ? Math.Min(6, ActualHeight / 2) : 4;
        var share = double.IsFinite(Fraction) ? Math.Clamp(Fraction, 0, 1) : 0;
        if (total <= 0 || share <= 0)
        {
            // Nothing to show still leaves a stub, so the chart keeps its baseline.
            var stub = Flat ? new Rect(0, 0, Math.Min(4, full), ActualHeight) : new Rect(0, ActualHeight - Math.Min(4, full), ActualWidth, Math.Min(4, full));
            drawingContext.DrawRoundedRectangle(Faint(TrackBrush), null, stub, Math.Min(radius, 2), Math.Min(radius, 2));
            return;
        }

        var length = Math.Max(full * share, 4) * Math.Clamp(Growth, 0, 1);
        if (length <= 0)
        {
            return;
        }

        var whole = Flat ? new Rect(0, 0, length, ActualHeight) : new Rect(0, ActualHeight - length, ActualWidth, length);
        drawingContext.PushClip(new RectangleGeometry(whole, radius, radius));
        var at = 0d;
        foreach (var (amount, brush) in parts)
        {
            var size = length * amount / total;
            var rect = Flat
                ? new Rect(at, 0, size, ActualHeight)
                : new Rect(0, ActualHeight - at - size, ActualWidth, size);
            drawingContext.DrawRectangle(brush ?? FallbackBrush, null, rect);
            at += size;
        }

        drawingContext.Pop();
    }

    // A frozen copy at a low opacity; the theme's own brush is shared, so it is never changed.
    private static Brush Faint(Brush brush)
    {
        var copy = brush.CloneCurrentValue();
        copy.Opacity = 0.3;
        copy.Freeze();
        return copy;
    }

    // Grows from nothing to its length, or with reduce motion fades in where it stands.
    private void GrowIn()
    {
        if (ReduceMotion)
        {
            BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, FadeDuration));
            return;
        }

        BeginAnimation(GrowthProperty, new DoubleAnimation(0, 1, GrowDuration) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } });
    }
}

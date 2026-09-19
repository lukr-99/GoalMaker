using System.Windows;
using System.Windows.Media;

namespace GoalMaker.App.Controls;

/// <summary>
/// One day of a review's look back: a bar as tall as the day's share of the busiest day, in the accent
/// when something was done and a faint track when nothing was (docs/reviews.md).
/// </summary>
public sealed class DayBar : FrameworkElement
{
    public static readonly DependencyProperty DoneProperty = DependencyProperty.Register(
        nameof(Done), typeof(int), typeof(DayBar), new FrameworkPropertyMetadata(0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty MostProperty = DependencyProperty.Register(
        nameof(Most), typeof(int), typeof(DayBar), new FrameworkPropertyMetadata(1, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty AccentBrushProperty = DependencyProperty.Register(
        nameof(AccentBrush), typeof(Brush), typeof(DayBar), new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(DayBar), new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public DayBar()
    {
        SetResourceReference(AccentBrushProperty, "GM.AccentBrush");
        SetResourceReference(TrackBrushProperty, "GM.OutlineBrush");
    }

    /// <summary>How many tasks were done that day.</summary>
    public int Done
    {
        get => (int)GetValue(DoneProperty);
        set => SetValue(DoneProperty, value);
    }

    /// <summary>The busiest day of the period, which fills the bar.</summary>
    public int Most
    {
        get => (int)GetValue(MostProperty);
        set => SetValue(MostProperty, value);
    }

    public Brush AccentBrush
    {
        get => (Brush)GetValue(AccentBrushProperty);
        set => SetValue(AccentBrushProperty, value);
    }

    public Brush TrackBrush
    {
        get => (Brush)GetValue(TrackBrushProperty);
        set => SetValue(TrackBrushProperty, value);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        var width = Math.Max(0, ActualWidth - 8);
        if (width <= 0 || ActualHeight <= 0)
        {
            return;
        }

        var share = Done <= 0 ? 0 : Math.Clamp(Done / (double)Math.Max(1, Most), 0, 1);
        var height = 6 + ((ActualHeight - 6) * share);
        var brush = Done > 0 ? AccentBrush : Faint(TrackBrush);
        drawingContext.DrawRoundedRectangle(brush, null, new Rect(4, ActualHeight - height, width, height), 3, 3);
    }

    // A frozen copy at a low opacity; the theme's own brush is shared, so it is never changed.
    private static Brush Faint(Brush brush)
    {
        var copy = brush.CloneCurrentValue();
        copy.Opacity = 0.3;
        copy.Freeze();
        return copy;
    }
}

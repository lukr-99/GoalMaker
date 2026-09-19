using System.Windows;
using System.Windows.Automation;
using System.Windows.Media;

namespace GoalMaker.App.Controls;

/// <summary>
/// A ring filled to <see cref="Fraction"/> (0 to 1) in the accent over a faint track (design spec,
/// color roles: the accent is for checks, progress and rings). Whatever sits on top of it (an emoji,
/// a percentage) is laid out by the page.
/// </summary>
public sealed class GoalRing : FrameworkElement
{
    public static readonly DependencyProperty FractionProperty = DependencyProperty.Register(
        nameof(Fraction), typeof(double), typeof(GoalRing), new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender, OnFractionChanged));

    public static readonly DependencyProperty RingBrushProperty = DependencyProperty.Register(
        nameof(RingBrush), typeof(Brush), typeof(GoalRing), new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(GoalRing), new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty ThicknessProperty = DependencyProperty.Register(
        nameof(Thickness), typeof(double), typeof(GoalRing), new FrameworkPropertyMetadata(4.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public GoalRing()
    {
        SetResourceReference(RingBrushProperty, "GM.AccentBrush");
        SetResourceReference(TrackBrushProperty, "GM.OutlineBrush");
        AutomationProperties.SetItemStatus(this, "0%");
    }

    public double Fraction
    {
        get => (double)GetValue(FractionProperty);
        set => SetValue(FractionProperty, value);
    }

    public Brush RingBrush
    {
        get => (Brush)GetValue(RingBrushProperty);
        set => SetValue(RingBrushProperty, value);
    }

    public Brush TrackBrush
    {
        get => (Brush)GetValue(TrackBrushProperty);
        set => SetValue(TrackBrushProperty, value);
    }

    public double Thickness
    {
        get => (double)GetValue(ThicknessProperty);
        set => SetValue(ThicknessProperty, value);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        var size = Math.Min(ActualWidth, ActualHeight);
        if (size <= Thickness)
        {
            return;
        }

        var radius = (size - Thickness) / 2;
        var center = new Point(ActualWidth / 2, ActualHeight / 2);
        // A faint copy of the track brush; the theme's own brush is shared, so it is never changed.
        var trackBrush = TrackBrush.CloneCurrentValue();
        trackBrush.Opacity = 0.35;
        trackBrush.Freeze();
        drawingContext.DrawEllipse(null, new Pen(trackBrush, Thickness), center, radius, radius);
        var fraction = Math.Clamp(Fraction, 0, 1);
        if (fraction <= 0)
        {
            return;
        }

        var ring = new Pen(RingBrush, Thickness) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
        if (fraction >= 1)
        {
            drawingContext.DrawEllipse(null, ring, center, radius, radius);
            return;
        }

        // Clockwise from twelve o'clock.
        var angle = fraction * 2 * Math.PI;
        var start = new Point(center.X, center.Y - radius);
        var end = new Point(center.X + (radius * Math.Sin(angle)), center.Y - (radius * Math.Cos(angle)));
        var geometry = new StreamGeometry();
        using (var context = geometry.Open())
        {
            context.BeginFigure(start, isFilled: false, isClosed: false);
            context.ArcTo(end, new Size(radius, radius), 0, fraction > 0.5, SweepDirection.Clockwise, isStroked: true, isSmoothJoin: false);
        }

        geometry.Freeze();
        drawingContext.DrawGeometry(null, ring, geometry);
    }

    private static void OnFractionChanged(DependencyObject target, DependencyPropertyChangedEventArgs e) =>
        AutomationProperties.SetItemStatus(target, Math.Clamp((double)e.NewValue, 0, 1).ToString("P0", System.Globalization.CultureInfo.CurrentCulture));
}

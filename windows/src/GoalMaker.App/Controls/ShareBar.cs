using System.Windows;
using System.Windows.Media;

namespace GoalMaker.App.Controls;

/// <summary>
/// A bar filled by a share from 0 to 1 (docs/stats.md): upright for a chart column, flat for a rate on
/// a row. An empty share still leaves the faint track, so a chart keeps its shape.
/// </summary>
public sealed class ShareBar : FrameworkElement
{
    public static readonly DependencyProperty FractionProperty = DependencyProperty.Register(
        nameof(Fraction), typeof(double), typeof(ShareBar), new FrameworkPropertyMetadata(0d, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty FilledProperty = DependencyProperty.Register(
        nameof(Filled), typeof(bool), typeof(ShareBar), new FrameworkPropertyMetadata(true, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty FlatProperty = DependencyProperty.Register(
        nameof(Flat), typeof(bool), typeof(ShareBar), new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackProperty = DependencyProperty.Register(
        nameof(Track), typeof(bool), typeof(ShareBar), new FrameworkPropertyMetadata(true, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty AccentBrushProperty = DependencyProperty.Register(
        nameof(AccentBrush), typeof(Brush), typeof(ShareBar), new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(ShareBar), new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

    public ShareBar()
    {
        SetResourceReference(AccentBrushProperty, "GM.AccentBrush");
        SetResourceReference(TrackBrushProperty, "GM.OutlineBrush");
    }

    /// <summary>How much of the bar is filled, 0 to 1.</summary>
    public double Fraction
    {
        get => (double)GetValue(FractionProperty);
        set => SetValue(FractionProperty, value);
    }

    /// <summary>Whether the bar stands for anything at all; an empty month draws only its track.</summary>
    public bool Filled
    {
        get => (bool)GetValue(FilledProperty);
        set => SetValue(FilledProperty, value);
    }

    /// <summary>Flat fills from the left, upright fills from the bottom.</summary>
    public bool Flat
    {
        get => (bool)GetValue(FlatProperty);
        set => SetValue(FlatProperty, value);
    }

    /// <summary>Whether the whole bar is drawn faintly behind the fill: a share has a whole, a count has not.</summary>
    public bool Track
    {
        get => (bool)GetValue(TrackProperty);
        set => SetValue(TrackProperty, value);
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
        if (ActualWidth <= 0 || ActualHeight <= 0)
        {
            return;
        }

        var share = double.IsFinite(Fraction) ? Math.Clamp(Fraction, 0, 1) : 0;
        var radius = Flat ? Math.Min(3, ActualHeight / 2) : 4;
        if (Track)
        {
            drawingContext.DrawRoundedRectangle(Faint(TrackBrush), null, new Rect(0, 0, ActualWidth, ActualHeight), radius, radius);
        }

        if (!Filled && Track)
        {
            return;
        }

        // Without a track an empty column still leaves a stub, so the chart keeps its baseline.
        var brush = Filled ? AccentBrush : Faint(TrackBrush);
        var length = Filled ? Math.Max((Flat ? ActualWidth : ActualHeight) * share, 4) : 4;
        var rect = Flat
            ? new Rect(0, 0, length, ActualHeight)
            : new Rect(0, ActualHeight - length, ActualWidth, length);
        drawingContext.DrawRoundedRectangle(brush, null, rect, radius, radius);
    }

    // A frozen copy at a low opacity; the theme's own brush is shared, so it is never changed.
    private static Brush Faint(Brush brush)
    {
        var copy = brush.CloneCurrentValue();
        copy.Opacity = 0.25;
        copy.Freeze();
        return copy;
    }
}

using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace GoalMaker.App.Controls;

/// <summary>
/// The mark's trend line on its own: a line that zigzags up to the top right and ends in an arrow head,
/// drawn across the whole control. It draws itself once when it loads, at the pace of the logo's arrow,
/// and stands still with reduce motion (GM.ReduceMotion). Decoration only, so it is hidden from screen
/// readers.
/// </summary>
public sealed class TrendStroke : FrameworkElement
{
    public static readonly DependencyProperty StrokeProperty = DependencyProperty.Register(
        nameof(Stroke), typeof(Brush), typeof(TrendStroke), new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty StrokeThicknessProperty = DependencyProperty.Register(
        nameof(StrokeThickness), typeof(double), typeof(TrendStroke), new FrameworkPropertyMetadata(6.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty DelayProperty = DependencyProperty.Register(
        nameof(Delay), typeof(double), typeof(TrendStroke), new PropertyMetadata(0.0));

    public static readonly DependencyProperty ReduceMotionProperty = DependencyProperty.Register(
        nameof(ReduceMotion), typeof(bool), typeof(TrendStroke), new PropertyMetadata(false));

    private static readonly DependencyProperty DrawnProperty = DependencyProperty.Register(
        "Drawn", typeof(double), typeof(TrendStroke), new FrameworkPropertyMetadata(1.0, FrameworkPropertyMetadataOptions.AffectsRender));

    // The line's corners on a unit box, from the bottom left up to the top right.
    private static readonly Point[] Corners = [new(0, 1), new(0.28, 0.58), new(0.48, 0.76), new(0.94, 0.06)];

    private const double HeadFrom = 0.8;
    private const double DrawMilliseconds = 800;

    public TrendStroke()
    {
        IsHitTestVisible = false;
        Focusable = false;
        SetResourceReference(ReduceMotionProperty, "GM.ReduceMotion");
        Loaded += (_, _) => Play();
    }

    // Decoration has no peer, so screen readers never meet it.
    protected override System.Windows.Automation.Peers.AutomationPeer? OnCreateAutomationPeer() => null;

    public Brush Stroke
    {
        get => (Brush)GetValue(StrokeProperty);
        set => SetValue(StrokeProperty, value);
    }

    public double StrokeThickness
    {
        get => (double)GetValue(StrokeThicknessProperty);
        set => SetValue(StrokeThicknessProperty, value);
    }

    /// <summary>How long to wait, in milliseconds, before the line starts drawing.</summary>
    public double Delay
    {
        get => (double)GetValue(DelayProperty);
        set => SetValue(DelayProperty, value);
    }

    public bool ReduceMotion
    {
        get => (bool)GetValue(ReduceMotionProperty);
        set => SetValue(ReduceMotionProperty, value);
    }

    /// <summary>The share of the line drawn so far, 0 to 1, for the layout tests.</summary>
    public double Drawn => (double)GetValue(DrawnProperty);

    private void Play()
    {
        if (ReduceMotion)
        {
            BeginAnimation(DrawnProperty, null);
            SetValue(DrawnProperty, 1.0);
            return;
        }

        var draw = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(DrawMilliseconds))
        {
            BeginTime = TimeSpan.FromMilliseconds(Delay),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
            FillBehavior = FillBehavior.HoldEnd,
        };
        SetValue(DrawnProperty, 0.0);
        BeginAnimation(DrawnProperty, draw);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        var thickness = StrokeThickness;
        var inset = thickness * 2;
        var width = ActualWidth - (inset * 2);
        var height = ActualHeight - (inset * 2);
        if (width <= 0 || height <= 0)
        {
            return;
        }

        var points = Corners.Select(corner => new Point(inset + (corner.X * width), inset + (corner.Y * height))).ToArray();
        var lengths = points.Zip(points.Skip(1), (from, to) => (to - from).Length).ToArray();
        var reach = lengths.Sum() * Math.Clamp(Drawn, 0, 1);
        var pen = new Pen(Stroke, thickness) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round };

        var figure = new PathFigure { StartPoint = points[0] };
        for (var leg = 0; leg < lengths.Length && reach > 0; leg++)
        {
            var share = Math.Min(1, reach / lengths[leg]);
            figure.Segments.Add(new LineSegment(points[leg] + ((points[leg + 1] - points[leg]) * share), true));
            reach -= lengths[leg];
        }

        drawingContext.DrawGeometry(null, pen, new PathGeometry([figure]));

        // The head lands as the line reaches it, along the last leg's direction.
        var head = Math.Clamp((Drawn - HeadFrom) / (1 - HeadFrom), 0, 1);
        if (head <= 0)
        {
            return;
        }

        var tip = points[^1];
        var along = tip - points[^2];
        along.Normalize();
        var across = new Vector(-along.Y, along.X);
        var size = thickness * 2.6;
        var arrow = new StreamGeometry();
        using (var context = arrow.Open())
        {
            context.BeginFigure(tip + (along * thickness), true, true);
            context.LineTo(tip - (along * size) + (across * size * 0.8), true, false);
            context.LineTo(tip - (along * size) - (across * size * 0.8), true, false);
        }

        drawingContext.PushOpacity(head);
        drawingContext.DrawGeometry(Stroke, null, arrow);
        drawingContext.Pop();
    }
}

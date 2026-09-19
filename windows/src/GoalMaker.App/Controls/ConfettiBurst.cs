using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace GoalMaker.App.Controls;

/// <summary>
/// A light burst of confetti over a page for a goal hit or a streak milestone (design spec: "a lighter
/// burst on Windows"). It never takes the mouse; callers skip it when motion is reduced.
/// </summary>
public sealed class ConfettiBurst : Canvas
{
    private const int Pieces = 40;
    private const double Seconds = 1.3;
    private const double Drag = 3.0;
    private const double Gravity = 420;
    private readonly Random random = new();
    private readonly List<(Rectangle Shape, Point Origin, Vector Velocity, double Spin)> pieces = [];
    private readonly Stopwatch clock = new();
    private bool running;

    public ConfettiBurst()
    {
        IsHitTestVisible = false;
    }

    /// <summary>Throws one burst from the upper middle of the page in <paramref name="colors"/>.</summary>
    public void Burst(IReadOnlyList<Brush> colors)
    {
        if (colors.Count == 0 || ActualWidth <= 0)
        {
            return;
        }

        Children.Clear();
        pieces.Clear();
        var origin = new Point(ActualWidth / 2, ActualHeight * 0.3);
        for (var index = 0; index < Pieces; index++)
        {
            var shape = new Rectangle
            {
                Width = 5 + (random.NextDouble() * 5),
                Height = 3 + (random.NextDouble() * 4),
                Fill = colors[index % colors.Count],
                RenderTransformOrigin = new Point(0.5, 0.5),
                RenderTransform = new RotateTransform(random.NextDouble() * 360),
            };
            var angle = random.NextDouble() * 2 * Math.PI;
            var speed = 300 + (random.NextDouble() * 500);
            pieces.Add((shape, origin, new Vector(Math.Cos(angle) * speed, (Math.Sin(angle) * speed) - 250), (random.NextDouble() * 720) - 360));
            Children.Add(shape);
        }

        clock.Restart();
        if (!running)
        {
            running = true;
            CompositionTarget.Rendering += OnRendering;
        }
    }

    // Air slows the throw, gravity pulls the pieces down, and they fade out towards the end.
    private void OnRendering(object? sender, EventArgs e)
    {
        var t = clock.Elapsed.TotalSeconds;
        if (t >= Seconds)
        {
            CompositionTarget.Rendering -= OnRendering;
            running = false;
            Children.Clear();
            pieces.Clear();
            return;
        }

        var travelled = (1 - Math.Exp(-Drag * t)) / Drag;
        var fade = 1 - Math.Pow(t / Seconds, 2);
        foreach (var (shape, origin, velocity, spin) in pieces)
        {
            SetLeft(shape, origin.X + (velocity.X * travelled));
            SetTop(shape, origin.Y + (velocity.Y * travelled) + (0.5 * Gravity * t * t));
            shape.Opacity = fade;
            ((RotateTransform)shape.RenderTransform).Angle += spin / 60;
        }
    }
}

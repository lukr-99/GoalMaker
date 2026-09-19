using System.IO;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using GoalMaker.Core.Design;

namespace GoalMaker.App.Controls;

/// <summary>
/// The GoalMaker mark in the current theme's logo colors (the GM.Logo* resources): the tile, the G and
/// the trend arrow (contracts/design/logo.json). When the theme changes it recolors, the arrow draws
/// itself again and the tile gives a small bounce; with reduce motion the colors just switch.
/// <see cref="Render"/> draws the same mark into a bitmap for the window and tray icons.
/// </summary>
public sealed class GoalMakerLogo : FrameworkElement
{
    public static readonly DependencyProperty MarkProperty = DependencyProperty.Register(
        nameof(Mark), typeof(LogoMark), typeof(GoalMakerLogo), new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty TileColorProperty = ColorProperty(nameof(TileColor), logo => logo.tile);

    public static readonly DependencyProperty LetterColorProperty = ColorProperty(nameof(LetterColor), logo => logo.letter);

    public static readonly DependencyProperty ArrowColorProperty = ColorProperty(nameof(ArrowColor), logo => logo.arrow);

    public static readonly DependencyProperty ReduceMotionProperty = DependencyProperty.Register(
        nameof(ReduceMotion), typeof(bool), typeof(GoalMakerLogo), new PropertyMetadata(false));

    // How much of the arrow is drawn, 0 to 1; replayed when the theme changes.
    private static readonly DependencyProperty DrawnProperty = DependencyProperty.Register(
        "Drawn", typeof(double), typeof(GoalMakerLogo), new FrameworkPropertyMetadata(1.0, FrameworkPropertyMetadataOptions.AffectsRender));

    // Design spec, motion: emphasized is 400 ms; the arrow takes two of those to draw.
    private static readonly Duration RecolorTime = new(TimeSpan.FromMilliseconds(400));
    private static readonly Duration RedrawTime = new(TimeSpan.FromMilliseconds(800));
    private const double HeadFrom = 0.8;

    private readonly SolidColorBrush tile = new(Colors.Black);
    private readonly SolidColorBrush letter = new(Colors.White);
    private readonly SolidColorBrush arrow = new(Colors.White);
    private readonly ScaleTransform bounce = new(1, 1);
    private bool replayQueued;

    public GoalMakerLogo()
    {
        SetResourceReference(MarkProperty, "GM.LogoMark");
        SetResourceReference(TileColorProperty, "GM.LogoTileColor");
        SetResourceReference(LetterColorProperty, "GM.LogoLetterColor");
        SetResourceReference(ArrowColorProperty, "GM.LogoArrowColor");
        SetResourceReference(ReduceMotionProperty, "GM.ReduceMotion");
        RenderTransform = bounce;
        RenderTransformOrigin = new Point(0.5, 0.5);
        AutomationProperties.SetName(this, "GoalMaker");
    }

    public LogoMark? Mark
    {
        get => (LogoMark?)GetValue(MarkProperty);
        set => SetValue(MarkProperty, value);
    }

    public Color TileColor
    {
        get => (Color)GetValue(TileColorProperty);
        set => SetValue(TileColorProperty, value);
    }

    public Color LetterColor
    {
        get => (Color)GetValue(LetterColorProperty);
        set => SetValue(LetterColorProperty, value);
    }

    public Color ArrowColor
    {
        get => (Color)GetValue(ArrowColorProperty);
        set => SetValue(ArrowColorProperty, value);
    }

    public bool ReduceMotion
    {
        get => (bool)GetValue(ReduceMotionProperty);
        set => SetValue(ReduceMotionProperty, value);
    }

    /// <summary>The mark in <paramref name="colors"/> as a square bitmap <paramref name="pixels"/> wide.</summary>
    public static BitmapSource Render(LogoMark mark, LogoColors colors, int pixels)
    {
        var visual = new DrawingVisual();
        using (var context = visual.RenderOpen())
        {
            context.PushTransform(new ScaleTransform(pixels / mark.Size, pixels / mark.Size));
            Draw(context, mark, Solid(colors.Tile), Solid(colors.Letter), Solid(colors.Arrow), 1);
        }

        var bitmap = new RenderTargetBitmap(pixels, pixels, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(visual);
        bitmap.Freeze();
        return bitmap;
    }

    /// <summary>
    /// The mark in <paramref name="colors"/> as an .ico file's bytes, with PNG images from 16 to 64 pixels
    /// (the tray reads icons from files only).
    /// </summary>
    public static byte[] IconFile(LogoMark mark, LogoColors colors)
    {
        int[] sizes = [16, 20, 24, 32, 40, 48, 64];
        var images = sizes.Select(size =>
        {
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(Render(mark, colors, size)));
            using var stream = new MemoryStream();
            encoder.Save(stream);
            return stream.ToArray();
        }).ToList();
        using var file = new MemoryStream();
        using var writer = new BinaryWriter(file);
        writer.Write((ushort)0);
        writer.Write((ushort)1);
        writer.Write((ushort)images.Count);
        var offset = 6 + (16 * images.Count);
        for (var index = 0; index < images.Count; index++)
        {
            writer.Write((byte)sizes[index]);
            writer.Write((byte)sizes[index]);
            writer.Write((byte)0);
            writer.Write((byte)0);
            writer.Write((ushort)1);
            writer.Write((ushort)32);
            writer.Write(images[index].Length);
            writer.Write(offset);
            offset += images[index].Length;
        }

        foreach (var image in images)
        {
            writer.Write(image);
        }

        writer.Flush();
        return file.ToArray();
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        if (Mark is not { } mark)
        {
            return;
        }

        var scale = Math.Min(ActualWidth, ActualHeight) / mark.Size;
        drawingContext.PushTransform(new TranslateTransform((ActualWidth - (mark.Size * scale)) / 2, (ActualHeight - (mark.Size * scale)) / 2));
        drawingContext.PushTransform(new ScaleTransform(scale, scale));
        Draw(drawingContext, mark, tile, letter, arrow, (double)GetValue(DrawnProperty));
        drawingContext.Pop();
        drawingContext.Pop();
    }

    protected override Size MeasureOverride(Size availableSize) => new(
        double.IsInfinity(availableSize.Width) ? 48 : availableSize.Width,
        double.IsInfinity(availableSize.Height) ? 48 : availableSize.Height);

    private static void Draw(DrawingContext context, LogoMark mark, Brush tile, Brush letter, Brush arrow, double drawn)
    {
        context.DrawRoundedRectangle(tile, null, new Rect(0, 0, mark.Size, mark.Size), mark.TileCorner, mark.TileCorner);
        var round = new Pen(letter, mark.Stroke) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
        context.DrawGeometry(null, round, Geometry.Parse(mark.Letter));
        var line = new Pen(arrow, mark.Stroke) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round };
        context.DrawGeometry(null, line, drawn >= 1 ? Geometry.Parse(mark.Trend) : Partial(Geometry.Parse(mark.Trend), drawn));

        // The head lands as the line reaches it.
        var head = Math.Clamp((drawn - HeadFrom) / (1 - HeadFrom), 0, 1);
        if (head > 0)
        {
            context.PushOpacity(head);
            context.DrawGeometry(arrow, null, Geometry.Parse(mark.Head));
            context.Pop();
        }
    }

    // The first share of a polyline, by length.
    private static Geometry Partial(Geometry path, double share)
    {
        var points = new List<Point>();
        foreach (var figure in PathGeometry.CreateFromGeometry(path).Figures)
        {
            points.Add(figure.StartPoint);
            foreach (var segment in figure.Segments)
            {
                switch (segment)
                {
                    case LineSegment single:
                        points.Add(single.Point);
                        break;
                    case PolyLineSegment many:
                        points.AddRange(many.Points);
                        break;
                }
            }
        }

        var total = points.Zip(points.Skip(1), (a, b) => (b - a).Length).Sum();
        var left = total * Math.Clamp(share, 0, 1);
        var drawnPoints = new List<Point> { points[0] };
        for (var index = 1; index < points.Count && left > 0; index++)
        {
            var step = points[index] - points[index - 1];
            if (step.Length <= left)
            {
                drawnPoints.Add(points[index]);
                left -= step.Length;
            }
            else
            {
                drawnPoints.Add(points[index - 1] + (step * (left / step.Length)));
                left = 0;
            }
        }

        var geometry = new StreamGeometry();
        using (var context = geometry.Open())
        {
            context.BeginFigure(drawnPoints[0], isFilled: false, isClosed: false);
            context.PolyLineTo(drawnPoints.Skip(1).ToList(), isStroked: true, isSmoothJoin: true);
        }

        geometry.Freeze();
        return geometry;
    }

    private static SolidColorBrush Solid(uint argb)
    {
        var brush = new SolidColorBrush(Color.FromArgb((byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb));
        brush.Freeze();
        return brush;
    }

    private static DependencyProperty ColorProperty(string name, Func<GoalMakerLogo, SolidColorBrush> brush) => DependencyProperty.Register(
        name,
        typeof(Color),
        typeof(GoalMakerLogo),
        new PropertyMetadata(Colors.Transparent, (target, change) => ((GoalMakerLogo)target).Recolor(brush((GoalMakerLogo)target), (Color)change.NewValue)));

    // The first colors just apply; a theme change on screen fades them and replays the arrow once.
    private void Recolor(SolidColorBrush brush, Color color)
    {
        var animate = IsLoaded && IsVisible && !ReduceMotion;
        if (!animate)
        {
            brush.BeginAnimation(SolidColorBrush.ColorProperty, null);
            brush.Color = color;
            InvalidateVisual();
            return;
        }

        brush.BeginAnimation(SolidColorBrush.ColorProperty, new ColorAnimation(color, RecolorTime) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } });
        if (!replayQueued)
        {
            replayQueued = true;
            Dispatcher.BeginInvoke(Replay);
        }
    }

    private void Replay()
    {
        replayQueued = false;
        BeginAnimation(DrawnProperty, new DoubleAnimation(0, 1, RedrawTime) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseInOut } });
        var spring = new DoubleAnimation(0.88, 1, RecolorTime) { EasingFunction = new ElasticEase { Oscillations = 1, Springiness = 5 } };
        bounce.BeginAnimation(ScaleTransform.ScaleXProperty, spring);
        bounce.BeginAnimation(ScaleTransform.ScaleYProperty, spring);
    }
}

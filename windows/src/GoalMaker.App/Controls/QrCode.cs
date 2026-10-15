using System.Windows;
using System.Windows.Automation.Peers;
using System.Windows.Media;
using QRCoder;

namespace GoalMaker.App.Controls;

/// <summary>
/// A QR code for <see cref="Text"/>, dark squares on white in every theme with the quiet zone around
/// them, because phone cameras read that best. Square, as large as the room it gets.
/// </summary>
public sealed class QrCode : FrameworkElement
{
    public static readonly DependencyProperty TextProperty = DependencyProperty.Register(
        nameof(Text), typeof(string), typeof(QrCode),
        new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender, (element, _) => ((QrCode)element).modules = null));

    private bool[,]? modules;

    public QrCode()
    {
        SnapsToDevicePixels = true;
        RenderOptions.SetEdgeMode(this, EdgeMode.Aliased);
    }

    public string? Text
    {
        get => (string?)GetValue(TextProperty);
        set => SetValue(TextProperty, value);
    }

    /// <summary>The code's squares, the quiet zone included: true is dark.</summary>
    public static bool[,] Modules(string text)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(text, QRCodeGenerator.ECCLevel.M);
        var rows = data.ModuleMatrix;
        var size = rows.Count;
        var squares = new bool[size, size];
        for (var y = 0; y < size; y++)
        {
            for (var x = 0; x < size; x++)
            {
                squares[y, x] = rows[y][x];
            }
        }

        return squares;
    }

    protected override Size MeasureOverride(Size availableSize)
    {
        var side = Math.Min(availableSize.Width, availableSize.Height);
        return double.IsInfinity(side) ? new Size(0, 0) : new Size(side, side);
    }

    protected override void OnRender(DrawingContext drawingContext)
    {
        if (string.IsNullOrEmpty(Text))
        {
            return;
        }

        modules ??= Modules(Text);
        var count = modules.GetLength(0);
        var side = Math.Min(ActualWidth, ActualHeight);
        // Whole device pixels per square, so every square is the same size and the camera sees a grid.
        var dpi = VisualTreeHelper.GetDpi(this).DpiScaleX;
        var cell = Math.Floor(side * dpi / count) / dpi;
        if (cell <= 0)
        {
            return;
        }

        var origin = Math.Round((side - (cell * count)) / 2 * dpi) / dpi;
        drawingContext.DrawRectangle(Brushes.White, null, new Rect(0, 0, side, side));
        var geometry = new StreamGeometry();
        using (var context = geometry.Open())
        {
            for (var y = 0; y < count; y++)
            {
                for (var x = 0; x < count; x++)
                {
                    if (!modules[y, x])
                    {
                        continue;
                    }

                    var left = origin + (x * cell);
                    var top = origin + (y * cell);
                    context.BeginFigure(new Point(left, top), isFilled: true, isClosed: true);
                    context.PolyLineTo([new Point(left + cell, top), new Point(left + cell, top + cell), new Point(left, top + cell)], isStroked: false, isSmoothJoin: false);
                }
            }
        }

        geometry.Freeze();
        drawingContext.DrawGeometry(Brushes.Black, null, geometry);
    }

    // Screen readers name it through AutomationProperties.Name on the sign-in screen.
    protected override AutomationPeer OnCreateAutomationPeer() => new FrameworkElementAutomationPeer(this);
}

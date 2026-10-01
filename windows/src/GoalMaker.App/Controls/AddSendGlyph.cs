using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Controls;

/// <summary>
/// The icon in the bottom bar's round button (docs/composer.md): a plus while the line is empty, the
/// send arrow once something is typed. Switching spins the plus out and lifts the arrow in over the
/// theme's quick and standard durations; with reduce motion the two only cross-fade. Both take the
/// button's foreground.
/// </summary>
public sealed class AddSendGlyph : Grid
{
    public static readonly DependencyProperty IsSendProperty = DependencyProperty.Register(
        nameof(IsSend), typeof(bool), typeof(AddSendGlyph), new PropertyMetadata(false, (d, _) => ((AddSendGlyph)d).Show(animate: true)));

    public static readonly DependencyProperty ReduceMotionProperty = DependencyProperty.Register(
        nameof(ReduceMotion), typeof(bool), typeof(AddSendGlyph), new PropertyMetadata(false));

    public static readonly DependencyProperty QuickDurationProperty = DependencyProperty.Register(
        nameof(QuickDuration), typeof(Duration), typeof(AddSendGlyph), new PropertyMetadata(new Duration(TimeSpan.FromMilliseconds(120))));

    public static readonly DependencyProperty StandardDurationProperty = DependencyProperty.Register(
        nameof(StandardDuration), typeof(Duration), typeof(AddSendGlyph), new PropertyMetadata(new Duration(TimeSpan.FromMilliseconds(250))));

    private readonly Part plus;
    private readonly Part send;

    public AddSendGlyph()
    {
        plus = new Part(new SymbolIcon { Symbol = SymbolRegular.Add24, FontSize = 18 });
        send = new Part(new SymbolIcon { Symbol = SymbolRegular.Send24, Filled = true, FontSize = 16 });
        Children.Add(plus.Icon);
        Children.Add(send.Icon);
        SetResourceReference(ReduceMotionProperty, "GM.ReduceMotion");
        SetResourceReference(QuickDurationProperty, "GM.QuickDuration");
        SetResourceReference(StandardDurationProperty, "GM.StandardDuration");
        Show(animate: false);
    }

    /// <summary>True for the send arrow, false for the plus.</summary>
    public bool IsSend
    {
        get => (bool)GetValue(IsSendProperty);
        set => SetValue(IsSendProperty, value);
    }

    public bool ReduceMotion
    {
        get => (bool)GetValue(ReduceMotionProperty);
        set => SetValue(ReduceMotionProperty, value);
    }

    public Duration QuickDuration
    {
        get => (Duration)GetValue(QuickDurationProperty);
        set => SetValue(QuickDurationProperty, value);
    }

    public Duration StandardDuration
    {
        get => (Duration)GetValue(StandardDurationProperty);
        set => SetValue(StandardDurationProperty, value);
    }

    private void Show(bool animate)
    {
        var moving = animate && !ReduceMotion && IsLoaded;
        var fading = animate && IsLoaded;
        // Hidden, the plus has turned a little over a third of the way round and shrunk; the arrow waits below, small.
        plus.Go(IsSend ? 0 : 1, IsSend ? 135 : 0, IsSend ? 0.4 : 1, 0, fading, moving, QuickDuration, StandardDuration);
        send.Go(IsSend ? 1 : 0, 0, IsSend ? 1 : 0.5, IsSend ? 0 : 10, fading, moving, QuickDuration, StandardDuration);
    }

    // One of the two icons with the turn, size and lift it moves by.
    private sealed class Part
    {
        private readonly RotateTransform turn = new();
        private readonly ScaleTransform size = new();
        private readonly TranslateTransform lift = new();

        public Part(SymbolIcon icon)
        {
            Icon = icon;
            icon.HorizontalAlignment = HorizontalAlignment.Center;
            icon.VerticalAlignment = VerticalAlignment.Center;
            icon.RenderTransformOrigin = new Point(0.5, 0.5);
            icon.RenderTransform = new TransformGroup { Children = { size, turn, lift } };
        }

        public SymbolIcon Icon { get; }

        public void Go(double opacity, double angle, double scale, double offset, bool fade, bool move, Duration quick, Duration standard)
        {
            Set(Icon, OpacityProperty, opacity, fade ? quick : null);
            Duration? duration = move ? standard : null;
            Set(turn, RotateTransform.AngleProperty, angle, duration);
            Set(size, ScaleTransform.ScaleXProperty, scale, duration);
            Set(size, ScaleTransform.ScaleYProperty, scale, duration);
            Set(lift, TranslateTransform.YProperty, offset, duration);
        }

        private static void Set(IAnimatable target, DependencyProperty property, double value, Duration? duration)
        {
            if (duration is not { HasTimeSpan: true } time)
            {
                target.BeginAnimation(property, null);
                ((DependencyObject)target).SetValue(property, value);
                return;
            }

            target.BeginAnimation(property, new DoubleAnimation(value, time) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } });
        }
    }
}

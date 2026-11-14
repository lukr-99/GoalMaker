using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// The About page. All behavior is in <see cref="AboutViewModel"/>; this only plays the opening: the
/// mark draws its arrow and each block fades in and rises a little, a beat after the one above. With
/// reduce motion (GM.ReduceMotion) the page is simply there.
/// </summary>
public partial class AboutPage
{
    private const double RiseBy = 18;
    private const double StaggerMilliseconds = 70;

    public AboutPage(AboutViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        Loaded += (_, _) => PlayOpening();
    }

    private void PlayOpening()
    {
        var reduced = TryFindResource("GM.ReduceMotion") is true;
        var standard = TryFindResource("GM.StandardDuration") is Duration duration ? duration : new Duration(TimeSpan.FromMilliseconds(250));
        if (!reduced)
        {
            HeroLogo.PlayIntro();
        }

        var order = 0;
        foreach (UIElement block in Blocks.Children)
        {
            if (reduced)
            {
                block.BeginAnimation(OpacityProperty, null);
                block.RenderTransform = Transform.Identity;
                continue;
            }

            var begin = TimeSpan.FromMilliseconds(order++ * StaggerMilliseconds);
            var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
            var rise = new TranslateTransform(0, RiseBy);
            block.RenderTransform = rise;
            block.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, standard) { BeginTime = begin, EasingFunction = ease });
            rise.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(RiseBy, 0, standard) { BeginTime = begin, EasingFunction = ease });
        }
    }
}

using System.Globalization;
using System.Windows;
using System.Windows.Controls;

namespace GoalMaker.App.Controls;

/// <summary>
/// A screen or section title in the theme's heading font, uppercased when the theme shouts (Track).
/// WPF has no text-transform, so the casing follows the GM.HeadlineUppercase resource the theme
/// applier sets.
/// </summary>
public sealed class HeadlineText : TextBlock
{
    public static readonly DependencyProperty TitleProperty = DependencyProperty.Register(
        nameof(Title), typeof(string), typeof(HeadlineText), new PropertyMetadata(string.Empty, (target, _) => ((HeadlineText)target).Update()));

    public static readonly DependencyProperty UppercaseProperty = DependencyProperty.Register(
        nameof(Uppercase), typeof(bool), typeof(HeadlineText), new PropertyMetadata(false, (target, _) => ((HeadlineText)target).Update()));

    public HeadlineText()
    {
        SetResourceReference(UppercaseProperty, "GM.HeadlineUppercase");
        SetResourceReference(FontFamilyProperty, "GM.HeadingFont");
    }

    public string Title
    {
        get => (string)GetValue(TitleProperty);
        set => SetValue(TitleProperty, value);
    }

    public bool Uppercase
    {
        get => (bool)GetValue(UppercaseProperty);
        set => SetValue(UppercaseProperty, value);
    }

    private void Update() => Text = Uppercase ? Title.ToUpper(CultureInfo.CurrentUICulture) : Title;
}

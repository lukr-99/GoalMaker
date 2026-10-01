using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using GoalMaker.Core.Notes;

namespace GoalMaker.App.Controls;

/// <summary>
/// A task's notes or a letter in light Markdown (docs/archive.md): one text line per block, headings
/// in a larger semibold, list items with a bullet, bold, italic, and links that open in the browser.
/// </summary>
public sealed class MarkdownView : StackPanel
{
    // Punctuation that sits on the baseline, under the top of a slanted letter.
    private const string LowMarks = ".,;:_";

    public static readonly DependencyProperty MarkdownProperty = DependencyProperty.Register(
        nameof(Markdown), typeof(string), typeof(MarkdownView), new PropertyMetadata(string.Empty, (view, _) => ((MarkdownView)view).Render()));

    public string Markdown
    {
        get => (string)GetValue(MarkdownProperty);
        set => SetValue(MarkdownProperty, value);
    }

    private static void Open(object sender, System.Windows.Navigation.RequestNavigateEventArgs e)
    {
        Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri) { UseShellExecute = true })?.Dispose();
        e.Handled = true;
    }

    private void Render()
    {
        Children.Clear();
        foreach (var block in LightMarkdown.Parse(Markdown ?? string.Empty))
        {
            var line = new TextBlock { TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 2) };
            if (block.Heading > 0)
            {
                line.FontSize = block.Heading switch { 1 => 20, 2 => 17, _ => 15 };
                line.FontWeight = FontWeights.SemiBold;
                line.Margin = new Thickness(0, block.Heading == 1 ? 12 : 8, 0, 4);
            }

            if (block.Bullet)
            {
                line.Inlines.Add(new Run("\u2022  "));
            }

            for (var index = 0; index < block.Spans.Count; index++)
            {
                var span = block.Spans[index];
                Inline inline = new Run(span.Text);
                if (span.Link is { } link && Uri.TryCreate(link, UriKind.Absolute, out var address))
                {
                    var hyperlink = new Hyperlink(new Run(span.Text)) { NavigateUri = address };
                    hyperlink.RequestNavigate += Open;
                    inline = hyperlink;
                }

                if (span.Bold)
                {
                    inline.FontWeight = FontWeights.Bold;
                }

                if (span.Italic)
                {
                    // The theme's real italic face, or the upright one that WPF slants (ThemeApplier).
                    inline.FontStyle = FontStyles.Italic;
                    inline.SetResourceReference(TextElement.FontFamilyProperty, "GM.BodyItalicFont");
                }

                line.Inlines.Add(inline);
                if (span.Italic && index < block.Spans.Count - 1 && !LowMarks.Contains(block.Spans[index + 1].Text[0], StringComparison.Ordinal))
                {
                    // A slanted word's last letter leans over what follows ("fourtimes"); an upright thin
                    // space gives the room back. Not before a low mark, which the lean passes over, and
                    // empty when the theme has a real italic.
                    var gap = new Run();
                    gap.SetResourceReference(Run.TextProperty, "GM.ItalicGap");
                    line.Inlines.Add(gap);
                }
            }

            Children.Add(line);
        }
    }
}

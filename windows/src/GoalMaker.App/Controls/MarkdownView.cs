using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using GoalMaker.Core.Notes;

namespace GoalMaker.App.Controls;

/// <summary>
/// A task's notes in light Markdown (docs/archive.md): one text line per block, list items with a
/// bullet, bold, italic, and links that open in the browser.
/// </summary>
public sealed class MarkdownView : StackPanel
{
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
            if (block.Bullet)
            {
                line.Inlines.Add(new Run("\u2022  "));
            }

            foreach (var span in block.Spans)
            {
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
                    inline.FontStyle = FontStyles.Italic;
                }

                line.Inlines.Add(inline);
            }

            Children.Add(line);
        }
    }
}

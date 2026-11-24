using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Controls;

/// <summary>
/// A search result's title with the words the search matched lit up: strong, in the accent (docs/projects.md,
/// "Searching items"). <see cref="Lead"/>, an item's id, goes first, small and muted, in the same text,
/// so a screen reader reads them together.
/// </summary>
public sealed class HighlightedText : TextBlock
{
    public static readonly DependencyProperty PartsProperty = DependencyProperty.Register(
        nameof(Parts), typeof(IReadOnlyList<TextPart>), typeof(HighlightedText), new PropertyMetadata(null, (target, _) => ((HighlightedText)target).Update()));

    public static readonly DependencyProperty LeadProperty = DependencyProperty.Register(
        nameof(Lead), typeof(string), typeof(HighlightedText), new PropertyMetadata(string.Empty, (target, _) => ((HighlightedText)target).Update()));

    public IReadOnlyList<TextPart>? Parts
    {
        get => (IReadOnlyList<TextPart>?)GetValue(PartsProperty);
        set => SetValue(PartsProperty, value);
    }

    public string Lead
    {
        get => (string)GetValue(LeadProperty);
        set => SetValue(LeadProperty, value);
    }

    private void Update()
    {
        Inlines.Clear();
        if (Lead.Length > 0)
        {
            var lead = new Run(Lead) { FontFamily = new System.Windows.Media.FontFamily("Cascadia Mono, Consolas, Courier New"), FontSize = 11 };
            lead.SetResourceReference(TextElement.ForegroundProperty, "GM.TextMutedBrush");
            Inlines.Add(lead);
        }

        foreach (var part in Parts ?? [])
        {
            var run = new Run(part.Text);
            if (part.IsMatch)
            {
                run.SetResourceReference(TextElement.FontFamilyProperty, "GM.BodyStrongFont");
                run.SetResourceReference(TextElement.ForegroundProperty, "GM.AccentBrush");
            }

            Inlines.Add(run);
        }
    }
}

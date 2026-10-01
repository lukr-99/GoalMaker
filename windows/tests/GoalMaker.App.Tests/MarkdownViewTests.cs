using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Media;
using GoalMaker.App.Controls;
using GoalMaker.App.Theming;

namespace GoalMaker.App.Tests;

/// <summary>
/// How notes and letters build their text. An italic word swallowed the space after it on Windows
/// ("The bank call moved fourtimes"): the body faces have no italic, WPF slanted the upright one, and
/// the slanted last letter leaned over the space. Track now ships a real italic; the other themes put
/// an upright thin space after the word.
/// </summary>
public sealed class MarkdownViewTests
{
    private const string Archivo = "GoalMaker Archivo 400";
    private const string Outfit = "GoalMaker Outfit 400";

    [Fact]
    public void TheSpaceAfterAnItalicWordIsItsOwnUprightRunAfterAThinGap() => OnStaThread(() =>
    {
        var runs = Runs(Render("The bank call moved *four* times", realItalic: false).Single());

        Assert.Equal(["The bank call moved ", "four", ThemeApplier.ItalicGap, " times"], runs.Select(run => run.Text));
        Assert.Equal(FontStyles.Italic, runs[1].FontStyle);
        Assert.All([runs[0], runs[2], runs[3]], run => Assert.Equal(FontStyles.Normal, run.FontStyle));
    });

    [Fact]
    public void AnItalicWordInTheRealItalicFaceNeedsNoGap() => OnStaThread(() =>
    {
        var runs = Runs(Render("The bank call moved *four* times", realItalic: true).Single());

        Assert.Equal(["The bank call moved ", "four", string.Empty, " times"], runs.Select(run => run.Text));
        Assert.Equal(Archivo + " Italic", Name(runs[1].FontFamily));
        Assert.Equal(FontStyles.Normal, runs[3].FontStyle);
    });

    [Fact]
    public void TheGapComesBeforeATallMarkButNotALowOneOrTheEndOfALine() => OnStaThread(() =>
    {
        var lines = Render("Moved *four*, then (*four*) and *four*.\nEnds on *four*", realItalic: false);

        Assert.Equal(["Moved ", "four", ", then (", "four", ThemeApplier.ItalicGap, ") and ", "four", "."], Runs(lines[0]).Select(run => run.Text));
        Assert.Equal(["Ends on ", "four"], Runs(lines[1]).Select(run => run.Text));
    });

    [Fact]
    public void OnlyArchivoShipsARealItalicForTheBody()
    {
        Assert.True(ThemeApplier.Ships(Archivo + " Italic"));
        Assert.True(ThemeApplier.Ships(Archivo));
        Assert.False(ThemeApplier.Ships(Outfit + " Italic"));
        Assert.False(ThemeApplier.Ships("GoalMaker Plus Jakarta Sans 400 Italic"));
        Assert.False(ThemeApplier.Ships("GoalMaker Space Grotesk 400 Italic"));
    }

    [Fact]
    public void TheRealItalicIsNotSlantedAgainWhileAnUprightFaceIs() => OnStaThread(() =>
    {
        Assert.Equal(StyleSimulations.None, Simulations(Archivo + " Italic", FontWeights.Normal));
        Assert.Equal(StyleSimulations.ItalicSimulation, Simulations(Outfit, FontWeights.Normal));
        Assert.Equal(StyleSimulations.BoldSimulation, Simulations(Archivo + " Italic", FontWeights.Bold));
    });

    // A note in a theme whose body is Archivo (with its real italic) or Outfit (slanted by WPF), with the
    // two resources ThemeApplier sets.
    private static List<TextBlock> Render(string markdown, bool realItalic)
    {
        var view = new MarkdownView();
        var page = new Border { Child = view };
        page.Resources["GM.BodyItalicFont"] = ThemeApplier.Face(realItalic ? Archivo + " Italic" : Outfit);
        page.Resources["GM.ItalicGap"] = realItalic ? string.Empty : ThemeApplier.ItalicGap;
        view.Markdown = markdown;
        return [.. view.Children.Cast<TextBlock>()];
    }

    private static List<Run> Runs(TextBlock line) => [.. line.Inlines.Cast<Run>()];

    private static string Name(FontFamily family) => family.Source.Split('#')[^1];

    private static StyleSimulations Simulations(string face, FontWeight weight)
    {
        var typeface = new Typeface(ThemeApplier.Face(face), FontStyles.Italic, weight, FontStretches.Normal);
        Assert.True(typeface.TryGetGlyphTypeface(out var glyphs), face);
        return glyphs.StyleSimulations;
    }

    private static void OnStaThread(Action test)
    {
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                test();
            }
            catch (Exception exception)
            {
                failure = exception;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null)
        {
            throw new InvalidOperationException("The test failed on its STA thread", failure);
        }
    }
}

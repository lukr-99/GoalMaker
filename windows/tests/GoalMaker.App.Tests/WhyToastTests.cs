using System.IO;
using System.Xml.Linq;
using GoalMaker.App.Shell;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The why reminder's toast: what it says, its picture and what a click carries back (docs/life-goals.md).</summary>
public sealed class WhyToastTests
{
    private const string Id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static readonly LifeGoalItem Audi = new(Id, "Own an Audi R8", "I love how it sounds");

    private static IReadOnlyList<string> Texts(XElement toast) => [.. toast.Descendants("text").Select(text => text.Value)];

    [Fact]
    public void ItShowsTheTitleTheWhyTheTimeLeftAndThePicture()
    {
        var picture = Path.Combine(Path.GetTempPath(), "toast-pictures", Id + ".jpg");
        var toast = ToastReminderNotifications.WhyContent(Audi, "10 years left", picture);

        Assert.Equal(["Own an Audi R8", "I love how it sounds", "10 years left"], Texts(toast));
        Assert.Equal("attribution", (string?)toast.Descendants("text").Last().Attribute("placement"));
        var image = Assert.Single(toast.Descendants("image"));
        Assert.Equal("hero", (string?)image.Attribute("placement"));
        Assert.Equal(new Uri(picture).AbsoluteUri, (string?)image.Attribute("src"));
        Assert.Equal(new ToastActivation(ToastAction.Why, Id), ToastActivation.Parse((string?)toast.Attribute("launch")));
    }

    [Fact]
    public void WithoutAByDateOrACachedPictureItShowsJustTheWords()
    {
        var toast = ToastReminderNotifications.WhyContent(Audi, null, null);

        Assert.Equal(["Own an Audi R8", "I love how it sounds"], Texts(toast));
        Assert.Empty(toast.Descendants("image"));
    }
}

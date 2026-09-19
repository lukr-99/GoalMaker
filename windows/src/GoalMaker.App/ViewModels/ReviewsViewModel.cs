using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Reviews page (docs/reviews.md): the weeks and months a review can still be written for, and the
/// reviews already written, which open again to be read or carried on.
/// </summary>
public sealed partial class ReviewsViewModel : ObservableObject
{
    private readonly ReviewList reviews;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<string, DateOnly> open;

    [ObservableProperty]
    private bool isEmpty;

    public ReviewsViewModel(
        ReviewList reviews, ISettingsStore settings, IStrings strings, TimeProvider time, Action<string, DateOnly> open, Action<Action> runOnUi)
    {
        this.reviews = reviews;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.open = open;
        reviews.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>The periods a review can be written for: this week and month, and the ones just gone.</summary>
    public ObservableCollection<ReviewStartViewModel> ToWrite { get; } = [];

    /// <summary>The reviews with something in them, newest first.</summary>
    public ObservableCollection<ReviewStartViewModel> Past { get; } = [];

    public void Refresh()
    {
        var today = Today();
        var week = ReviewRules.PeriodStart(ReviewRules.Weekly, today);
        var month = ReviewRules.PeriodStart(ReviewRules.Monthly, today);
        var written = reviews.All().Where(review => review.Written).ToList();
        bool Done(string kind, DateOnly start) => written.Any(review => review.Kind == kind && review.PeriodStart == start);

        ToWrite.Clear();
        foreach (var (kind, start, label) in new (string Kind, DateOnly Start, string Label)[]
        {
            (ReviewRules.Weekly, ReviewLookBack.PreviousStart(ReviewRules.Weekly, week), "Reviews.LastWeek"),
            (ReviewRules.Weekly, week, "Reviews.ThisWeek"),
            (ReviewRules.Monthly, ReviewLookBack.PreviousStart(ReviewRules.Monthly, month), "Reviews.LastMonth"),
            (ReviewRules.Monthly, month, "Reviews.ThisMonth"),
        })
        {
            ToWrite.Add(new ReviewStartViewModel(
                strings.Get(label),
                PeriodText(kind, start),
                strings.Get(Done(kind, start) ? "Reviews.Written" : "Reviews.Start"),
                () => open(kind, start),
                null));
        }

        Past.Clear();
        foreach (var review in written)
        {
            var parts = new List<string>();
            if (review.Mood is { } mood)
            {
                parts.Add(strings.Get("Reviews.MoodShort", mood));
            }

            if (review.Energy is { } energy)
            {
                parts.Add(strings.Get("Reviews.EnergyShort", energy));
            }

            var answers = review.Reflections.Count(reflection => !string.IsNullOrWhiteSpace(reflection.Answer));
            if (answers > 0)
            {
                parts.Add(strings.Get("Reviews.AnswersShort", answers));
            }

            Past.Add(new ReviewStartViewModel(
                PeriodText(review.Kind, review.PeriodStart),
                string.Join(" · ", parts),
                strings.Get("Reviews.Read"),
                () => open(review.Kind, review.PeriodStart),
                () => reviews.Delete(review.Id)));
        }

        IsEmpty = Past.Count == 0;
    }

    /// <summary>"14 to 20 Sep" for a week, "September 2026" for a month, "2026" for a year.</summary>
    private string PeriodText(string kind, DateOnly start) => kind switch
    {
        ReviewRules.Monthly => start.ToString("MMMM yyyy", CultureInfo.CurrentCulture),
        ReviewRules.Yearly => start.Year.ToString(CultureInfo.CurrentCulture),
        _ => strings.Get(
            "Goals.Range",
            start.ToString(start.Month == start.AddDays(6).Month ? "%d" : "d MMM", CultureInfo.CurrentCulture),
            start.AddDays(6).ToString("d MMM", CultureInfo.CurrentCulture)),
    };

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}

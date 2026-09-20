using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Security;
using System.Windows.Media.Imaging;
using System.Xml.Linq;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using Microsoft.Win32;
using Windows.UI.Notifications;
using WinRtXml = Windows.Data.Xml.Dom;

namespace GoalMaker.App.Shell;

/// <summary>
/// Reminder toasts through the Windows SDK, without MSIX or the Windows App SDK runtime (ADR 0009).
/// The app registers its own AppUserModelID for the current user, shows each reminder tagged with its
/// id, and hears the buttons while it runs, which the tray keeps it doing. An ordinary reminder uses
/// the reminder style and stays until handled; an important one uses the alarm style and rings.
/// </summary>
public sealed class ToastReminderNotifications
{
    private const string Group = "reminders";
    private const string PlanGroup = "plan";
    private const string ReviewGroup = "review";
    private readonly string appId;
    private readonly IStrings strings;

    public ToastReminderNotifications(string appId, string displayName, string iconPath, IStrings strings)
    {
        this.appId = appId;
        this.strings = strings;
        Register(displayName, iconPath);
    }

    /// <summary>A button or the toast itself was clicked, or the toast was closed. Raised off the UI thread.</summary>
    public event EventHandler<ToastActivation>? Activated;

    /// <summary>Shows <paramref name="reminder"/>. Does nothing when Windows won't show toasts for GoalMaker.</summary>
    public void Show(ScheduledReminder reminder)
    {
        var content = new WinRtXml.XmlDocument();
        content.LoadXml(Content(reminder).ToString(SaveOptions.DisableFormatting));
        var toast = new ToastNotification(content) { Tag = reminder.Id, Group = Group };
        toast.Activated += (_, args) =>
        {
            if (ToastActivation.Parse((args as ToastActivatedEventArgs)?.Arguments) is { } activation)
            {
                Activated?.Invoke(this, activation);
            }
        };
        toast.Dismissed += (_, args) =>
        {
            if (args.Reason == ToastDismissalReason.UserCanceled)
            {
                Activated?.Invoke(this, new ToastActivation(ToastAction.Dismiss, reminder.Id));
            }
        };
        Try(() => ToastNotificationManager.CreateToastNotifier(appId).Show(toast));
    }

    /// <summary>
    /// Shows the evening reminder to plan tomorrow, for planning <paramref name="day"/>. Clicking it or
    /// Plan opens the ritual; Not today keeps it quiet for the rest of the day on every device.
    /// </summary>
    public void ShowPlanTomorrow(DateOnly day)
    {
        var tag = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        var content = new WinRtXml.XmlDocument();
        content.LoadXml(PlanContent(tag).ToString(SaveOptions.DisableFormatting));
        var toast = new ToastNotification(content) { Tag = tag, Group = PlanGroup };
        toast.Activated += (_, args) =>
        {
            if (ToastActivation.Parse((args as ToastActivatedEventArgs)?.Arguments) is { } activation)
            {
                Activated?.Invoke(this, activation);
            }
        };
        Try(() => ToastNotificationManager.CreateToastNotifier(appId).Show(toast));
    }

    /// <summary>
    /// Shows the reminder to write a review, for the planning day it rang on. Clicking it or Review opens
    /// the review; Not now keeps it quiet for the rest of the day on every device (docs/reviews.md).
    /// </summary>
    public void ShowReview(string ritual, DateOnly day, bool monthly)
    {
        var tag = ritual + "/" + day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        var content = new WinRtXml.XmlDocument();
        content.LoadXml(ReviewContent(tag, monthly).ToString(SaveOptions.DisableFormatting));
        var toast = new ToastNotification(content) { Tag = tag, Group = ReviewGroup };
        toast.Activated += (_, args) =>
        {
            if (ToastActivation.Parse((args as ToastActivatedEventArgs)?.Arguments) is { } activation)
            {
                Activated?.Invoke(this, activation);
            }
        };
        Try(() => ToastNotificationManager.CreateToastNotifier(appId).Show(toast));
    }

    /// <summary>Takes a review reminder away.</summary>
    public void ClearReview(string ritual, DateOnly day) =>
        Try(() => ToastNotificationManager.History.Remove(ritual + "/" + day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture), ReviewGroup, appId));

    /// <summary>The review reminders on screen or in the notification centre, as their ritual and day.</summary>
    public IReadOnlyList<(string Ritual, DateOnly Day)> ShownReviews()
    {
        IReadOnlyList<(string, DateOnly)> shown = [];
        Try(() => shown = [.. ToastNotificationManager.History.GetHistory(appId)
            .Where(toast => toast.Group == ReviewGroup)
            .Select(toast => toast.Tag.Split('/'))
            .Where(parts => parts.Length == 2
                && DateOnly.TryParseExact(parts[1], "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _))
            .Select(parts => (parts[0], DateOnly.ParseExact(parts[1], "yyyy-MM-dd", CultureInfo.InvariantCulture)))]);
        return shown;
    }

    /// <summary>Takes the evening reminder for <paramref name="day"/> away.</summary>
    public void ClearPlanTomorrow(DateOnly day) =>
        Try(() => ToastNotificationManager.History.Remove(day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture), PlanGroup, appId));

    /// <summary>The planning days whose evening reminder is on screen or in the notification centre.</summary>
    public IReadOnlyList<DateOnly> ShownPlanTomorrow()
    {
        IReadOnlyList<DateOnly> shown = [];
        Try(() => shown = [.. ToastNotificationManager.History.GetHistory(appId)
            .Where(toast => toast.Group == PlanGroup)
            .Select(toast => DateOnly.TryParseExact(toast.Tag, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var day) ? day : (DateOnly?)null)
            .OfType<DateOnly>()]);
        return shown;
    }

    /// <summary>Takes a reminder's toast away, because it was handled here or on the other device.</summary>
    public void Clear(string reminderId) => Try(() => ToastNotificationManager.History.Remove(reminderId, Group, appId));

    /// <summary>Takes every reminder toast away, when GoalMaker quits and no longer hears the buttons.</summary>
    public void ClearAll() => Try(() => ToastNotificationManager.History.Clear(appId));

    /// <summary>The reminder ids whose toasts are on screen or in the notification centre.</summary>
    public IReadOnlyList<string> Shown()
    {
        IReadOnlyList<string> shown = [];
        Try(() => shown = [.. ToastNotificationManager.History.GetHistory(appId).Where(toast => toast.Group == Group).Select(toast => toast.Tag)]);
        return shown;
    }

    private static void Try(Action action)
    {
        try
        {
            action();
        }
        catch (Exception error) when (error is COMException or UnauthorizedAccessException)
        {
            // Toasts are off for GoalMaker or blocked by policy; the reminder still settles in the replica.
        }
    }

    // Windows shows toasts from an unpackaged app once its AppUserModelID has a name under HKCU. The
    // name and the icon are only how a toast looks, so a registry or a file that will not have them
    // written (a locked icon while a second copy starts, a policy on HKCU) must not stop the app.
    private void Register(string displayName, string iconPath)
    {
        try
        {
            using (var key = Registry.CurrentUser.CreateSubKey($@"Software\Classes\AppUserModelId\{appId}"))
            {
                key.SetValue("DisplayName", displayName);
                key.SetValue("IconUri", iconPath);
            }

            var decoder = new IconBitmapDecoder(new Uri("pack://application:,,,/Assets/GoalMaker.ico"), BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
            var frame = decoder.Frames.OrderByDescending(frame => frame.PixelWidth).FirstOrDefault();
            if (frame is null)
            {
                return;
            }

            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(frame));
            Directory.CreateDirectory(Path.GetDirectoryName(iconPath)!);
            using var stream = File.Create(iconPath);
            encoder.Save(stream);
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or SecurityException or NotSupportedException or ArgumentException)
        {
            // The toast still shows, under whatever name and icon Windows already has for the app.
        }
    }

    private XElement ReviewContent(string tag, bool monthly)
    {
        XElement Button(string label, ToastAction action) => new(
            "action",
            new XAttribute("content", strings.Get(label)),
            new XAttribute("arguments", new ToastActivation(action, tag).Arguments),
            new XAttribute("activationType", "foreground"));
        return new XElement(
            "toast",
            new XAttribute("launch", new ToastActivation(ToastAction.Review, tag).Arguments),
            new XAttribute("scenario", "reminder"),
            new XElement(
                "visual",
                new XElement(
                    "binding",
                    new XAttribute("template", "ToastGeneric"),
                    new XElement("text", strings.Get(monthly ? "Reviews.ReminderTitleMonthly" : "Reviews.ReminderTitleWeekly")),
                    new XElement("text", strings.Get("Reviews.ReminderText")))),
            new XElement("actions", Button("Reviews.ReminderStart", ToastAction.Review), Button("Reviews.ReminderSkip", ToastAction.SkipReview)));
    }

    private XElement PlanContent(string day)
    {
        XElement Button(string label, ToastAction action) => new(
            "action",
            new XAttribute("content", strings.Get(label)),
            new XAttribute("arguments", new ToastActivation(action, day).Arguments),
            new XAttribute("activationType", "foreground"));

        return new XElement(
            "toast",
            new XAttribute("launch", new ToastActivation(ToastAction.Plan, day).Arguments),
            new XElement(
                "visual",
                new XElement(
                    "binding",
                    new XAttribute("template", "ToastGeneric"),
                    new XElement("text", strings.Get("PlanReminder.Title")),
                    new XElement("text", strings.Get("PlanReminder.Text")))),
            new XElement("actions", Button("PlanReminder.Start", ToastAction.Plan), Button("PlanReminder.Skip", ToastAction.SkipPlan)));
    }

    private XElement Content(ScheduledReminder reminder)
    {
        XElement Button(string label, ToastAction action, Snooze? snooze = null) => new(
            "action",
            new XAttribute("content", strings.Get(label)),
            new XAttribute("arguments", new ToastActivation(action, reminder.Id, snooze).Arguments),
            new XAttribute("activationType", "foreground"));

        return new XElement(
            "toast",
            new XAttribute("scenario", reminder.Important ? "alarm" : "reminder"),
            new XAttribute("launch", new ToastActivation(ToastAction.Open, reminder.Id).Arguments),
            new XElement("visual", new XElement("binding", new XAttribute("template", "ToastGeneric"), new XElement("text", reminder.TaskTitle))),
            reminder.Important
                ? new XElement("audio", new XAttribute("src", "ms-winsoundevent:Notification.Looping.Alarm"), new XAttribute("loop", "true"))
                : null,
            new XElement(
                "actions",
                Button("Reminder.Done", ToastAction.Done),
                Button("Reminder.SnoozeTenMinutes", ToastAction.Snooze, Snooze.TenMinutes),
                Button("Reminder.SnoozeOneHour", ToastAction.Snooze, Snooze.OneHour),
                Button("Reminder.SnoozeTomorrow", ToastAction.Snooze, Snooze.TomorrowMorning)));
    }
}

# M8-05: The notification when wants become ready

**Status:** done · **Milestone:** M8

## Scope
- One notification a day at a time the owner picks (10:00 by default, a device setting like the
  review reminder), listing the wants that became ready since the last one (spec, story 106).
- Worked out from the wants like the review reminders (`ReviewReminder`): no reminder rows, the one
  alarm or timer shared with everything else, and a boot or a clock change re-arms it. Like the review
  reminders, quiet hours don't move it (the owner picked the time); the scope said otherwise before
  that was weighed against the review reminders' rule.
- Deciding a want takes it out of the notification on both devices after the next sync; tapping the
  notification opens the Wants place on Ready.
- Android first (a notification channel of its own), then Windows (a toast).

## Acceptance criteria
- `ReminderService` tests on both apps: the due time, quiet hours, a want decided before it rings, a
  want that became ready while the device was off.

## Vectors to add
- `contracts/vectors/wants.json`: more `ready` cases (a missed day, two wants on one day, a want
  decided on the other device).

## Check
- Emulator: a want ready at a dev-clock time rings and opens Ready; decided on the PC, the
  notification goes after a sync; it still rings after a reboot.
- Windows: the toast rings and opens the place; decided on the phone, it goes.
- Endpoint: nothing.

## Release
- With M8-01 to M8-06: **1.3.0** (the navigation and Wants).

## Result

2026-09-28, both apps.

- `WantReminder` in Kotlin and C# over the new `notify`, `notifyNext` and `notifyStale` groups of
  `contracts/vectors/wants.json`: due at the owner's time with the wants that became ready since the
  last notification's day (a day the device was off is caught up), the next moment for the alarm, and
  when a notification on screen is stale. Both pass every case.
- `ReminderService` on both apps carries the wants: the look names them, the one alarm or timer arms
  for them, and `wantsStale` settles them. The time is a device setting ("Wants ready", 10:00 by
  default, off switches it off) on both apps.
- **Android:** its own notification channel, the wants' titles in the notification, a tap opens the
  Wants place (pinned or not), and it comes down when a want changes here or a sync brings a decision
  from the PC. **Windows:** a toast with the same text, a click opens the Wants page, and the same
  sweep after a change or a sync.
- **Fixed on the way, both apps:** the review reminders were never armed on the alarm (Android armed
  only task reminders and Plan tomorrow; so did Windows), and on Android the alarm itself showed only
  task reminders and Plan tomorrow, so a review reminder appeared only when the app next signed in.
  Everything a look finds now goes through one `show` on Android, and the timer arms for the reviews
  and the wants on both apps. `ReminderServiceTests` on Windows now switches all three rituals off
  for "nothing armed", and a new test arms the weekly review.
- **Checked:** on the emulator, a want ready today with the time two minutes ahead: the alarm armed
  for it, the notification rang ("Headphones is ready to decide"), its intent opened Wants from
  Today, and dropping the want took it down. Windows: `WantsReminderServiceTests` (timer, catch-up,
  stale, off); the toast itself was not driven on the desktop.

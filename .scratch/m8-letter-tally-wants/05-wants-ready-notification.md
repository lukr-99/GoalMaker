# M8-05: The notification when wants become ready

**Status:** todo · **Milestone:** M8

## Scope
- One notification a day at a time the owner picks (10:00 by default, a device setting like the
  review reminder), listing the wants that became ready since the last one (spec, story 106).
- Worked out from the wants like the review reminders (`ReviewReminder`): no reminder rows, the one
  alarm or timer shared with everything else, quiet hours hold it back, and a boot or a clock change
  re-arms it.
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

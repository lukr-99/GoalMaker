# M2-09: Reminders and notifications

**Status:** both apps done 2026-09-18; the evening ritual reminder left · **Milestone:** M2

## Scope
- Several reminders per task (fixed or relative), scheduled locally from the replica: Android exact
  alarms with reschedule on boot, time change and update; Windows through the tray app with toast
  notifications. Done and Snooze (10 min, 1 h, tomorrow morning) on every notification.
- Reminder state syncs and the other device cancels its copy; quiet hours; important reminders
  ring until handled; the evening Plan tomorrow reminder.
- The evening reminder opens the ritual (Android: a route to it; Windows: `--open plan` or
  `goalmaker://open/plan`, both exist) and is skipped when today's ritual already ran on either
  device, which needs a synced record of it (docs/plan-tomorrow.md: Later).

## Acceptance criteria
- A reminder fires on both devices; handling it on one clears the other; phone reminders survive a
  reboot with sync switched off.

## Result
- The shared rules, pinned by `contracts/vectors/reminders.json` and run by both apps
  ([reminders](../../docs/reminders.md)): when a reminder fires, what quiet hours do to that time,
  and where each snooze lands.
- Android: `ReminderList` over the replica, `ReminderSchedule` and `ReminderService`, one
  `AlarmManager` alarm at a time through `AlarmReminderScheduler`, notifications with Done and two
  snoozes, and `ReminderReceiver` for the alarm, the buttons, a reboot, a changed clock or time
  zone, and an app update. Quiet hours are a device setting; a long press on a task sets a reminder.
- Shared: each device remembers its last look, so a reminder is shown once and a device that was
  off catches up; after a sync it takes down notifications that went stale, which is how handling a
  reminder on one device clears the other. Both rules are in the vectors.
- Android: opening the app from a notification dismisses it; important reminders ring until handled.
- Windows (ADR 0009): toasts through the Windows SDK projection with Done and the three snoozes, one
  timer in the tray app via `TimerReminderScheduler`, a look again after sleep and clock changes,
  quiet hours in Settings, and a right-click menu on a task that sets or removes its reminders.

## Left
- The evening Plan tomorrow reminder, skipped when the ritual already ran on either device. It needs
  a synced record of the ritual (a server and a replica migration with pgTAP tests), which waits for
  Docker so the migration harness can run.
- Verifying a reminder on two devices at once, which needs the cloud project or the local stack.

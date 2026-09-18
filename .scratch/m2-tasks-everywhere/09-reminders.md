# M2-09: Reminders and notifications

**Status:** todo · **Milestone:** M2

## Scope
- Several reminders per task (fixed or relative), scheduled locally from the replica: Android exact
  alarms with reschedule on boot, time change and update; Windows through the tray app with toast
  notifications. Done and Snooze (10 min, 1 h, tomorrow morning) on every notification.
- Reminder state syncs and the other device cancels its copy; quiet hours; important reminders
  ring until handled; the evening Plan tomorrow reminder.

## Acceptance criteria
- A reminder fires on both devices; handling it on one clears the other; phone reminders survive a
  reboot with sync switched off.

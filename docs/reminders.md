# Reminders

When a reminder fires, and what quiet hours and snooze do to that time (spec: stories 51 to 58).
The rules are pinned by [`contracts/vectors/reminders.json`](../contracts/vectors/reminders.json)
and run by both apps.

Every rule here works in the device's local time. A reminder's stored `fire_at` is an instant, so
the app turns it into a local time before asking these rules anything, and turns the answer back
into an instant when it schedules the alarm. Quiet hours and the important flag are settings on the
device, so two devices in different zones each do the right thing.

## When a reminder fires

A reminder belongs to a task and is either **fixed** (`fire_at`, its own time) or **relative**
(`offset_minutes`, that many minutes before the task's planned time). A relative reminder needs the
task to have both a planned day and a planned time; without them it has no time to count back from
and never fires.

A reminder has no time at all when:

- the reminder or its task is deleted,
- the task is done or dropped, so there is nothing left to remind about,
- the reminder is already **dismissed** or **done**,
- it is **snoozed** without a time to come back at, or it is fixed without a `fire_at`.

A **snoozed** reminder fires at its `snoozed_until` and ignores its own time until then.

## Quiet hours

Quiet hours are a window in local time, for example 22:00 to 07:00. The window may cross midnight.
A window whose start and end are the same hour and minute is off.

An ordinary reminder due inside the window is **held back to the end of the window**, so a 23:30
reminder in a 22:00 to 07:00 window arrives at 07:00 the next morning. The start counts as inside
and the end counts as outside, so a reminder due at exactly 07:00 is not held.

An **important** reminder ignores quiet hours (spec, story 57). It fires at its own time and keeps
ringing until it is handled.

## Snooze

Every notification offers Done and three snoozes:

| Snooze | Comes back at |
|---|---|
| 10 minutes | ten minutes after the snooze |
| 1 hour | an hour after the snooze |
| Tomorrow morning | 08:00 on the day after the current planning day |

"Tomorrow morning" counts from the planning day ([lists](lists.md)), not the calendar date, so
snoozing at 01:30 with a 04:00 day start brings the reminder back at 08:00 that same morning, about
six hours later, which is the next planning day.

Quiet hours are applied again when the reminder comes back, so a snooze cannot push an ordinary
reminder into the night.

## Two devices

Handling a reminder writes its state (`dismissed`, `done`, or `snoozed` with a time), which syncs
like every other row, and the other device drops its copy on the next sync. Completing the task
anywhere settles its reminders too, because a reminder whose task is no longer open has no time.

Each device schedules from its own replica, so reminders keep working offline and after a reboot.

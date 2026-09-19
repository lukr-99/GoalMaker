# Reminders

When a reminder fires, what quiet hours and snooze do to that time, and what a device shows, arms
and takes down (spec: stories 51 to 58).
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

A notification offers Done and the snoozes below. Windows shows all three. Android allows three
buttons on a notification, so it shows Done, 10 minutes and Tomorrow morning; for an hour, set a new
reminder "in an hour" from the task's menu.

| Snooze | Comes back at |
|---|---|
| 10 minutes | ten minutes after the snooze |
| 1 hour | an hour after the snooze (Windows) |
| Tomorrow morning | 08:00 on the day after the current planning day |

"Tomorrow morning" counts from the planning day ([lists](lists.md)), not the calendar date, so
snoozing at 01:30 with a 04:00 day start brings the reminder back at 08:00 that same morning, about
six hours later, which is the next planning day.

Quiet hours are applied again when the reminder comes back, so a snooze cannot push an ordinary
reminder into the night.

## Looking at the reminders

A device keeps one alarm armed, for the soonest reminder still ahead (ties: the smaller id). When it
goes off, or the device wakes, reboots or has its clock changed, the device **looks**: it shows every
reminder that arrived after its previous look, up to now, and arms the next one. The time of the
last look stays on the device, so:

- a reminder is shown once, even if the owner opens the notification without handling it,
- a device that was off or asleep shows everything it missed when it comes back,
- a device that never looked starts from now, so installing the app doesn't replay old reminders.

## The evening Plan tomorrow reminder

Once per planning day, at a time the owner chooses (20:00 unless changed, or switched off), a
reminder offers to start Plan tomorrow ([plan tomorrow](plan-tomorrow.md)). It rings at the first
moment of the planning day whose clock shows that time, so 00:30 with a 04:00 day start still
belongs to the evening before. Quiet hours don't move it, because the owner picked the time.

It stays quiet on a day the ritual was already **done** or **skipped** ("Not today") on either
device. That is kept in the synced `ritual_runs` table: one row per ritual and planning day, whose
id is a UUID version 5 of `<owner>/<ritual>/<day>`, so both devices recording it make the same row.
A device that was away shows only today's reminder, never an earlier day's, and takes it down when
the ritual runs elsewhere or the planning day moves on. Pinned by the `ritual`, `ritualStale` and
`ritualIds` cases in [`contracts/vectors/reminders.json`](../contracts/vectors/reminders.json).

The time is a device setting (Settings, Planning), in half hours. The reminder shares the device's
one alarm with task reminders, so whichever comes first is armed. It offers **Plan**, which opens the
ritual, and **Not today**, which records the day as skipped. Reaching the ritual's summary card
records it as done. On Android it has its own notification channel, so the owner can silence it
apart from task reminders.

## Two devices

Handling a reminder writes its state (`dismissed`, `done`, or `snoozed` with a time), which syncs
like every other row. Opening the app from a notification counts as dismissing it. Completing the
task anywhere settles its reminders too, because a reminder whose task is no longer open has no time.

After every sync, a device takes down each notification on screen that has gone **stale**: its
reminder was handled or deleted, it was snoozed or its task moved so it is due later, or its task
finished. A notification whose reminder is still due stays.

Each device schedules from its own replica, so reminders keep working offline and after a reboot.

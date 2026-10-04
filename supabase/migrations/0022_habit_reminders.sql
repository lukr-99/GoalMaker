-- 0022: a habit can remind (docs/reminders.md, spec story 38: check in from the reminder notification).
--
-- remind_at is a time of day in the device's local time, like the rituals' reminder times. On every
-- day the habit is due, each device rings it at that time while the habit is still left (not done,
-- skipped, failed or paused), and its notification checks in, adds one or skips. Null means the habit
-- doesn't remind, which is how every habit from before reads, and an app from before 0022 leaves the
-- column out of its rows, so it keeps whatever the owner set elsewhere.

alter table public.habits
  add column remind_at time;

comment on column public.habits.remind_at is
  'The local time a habit reminds on the days it is due and still left; null for no reminder.';

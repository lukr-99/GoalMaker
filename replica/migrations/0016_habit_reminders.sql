-- 0016: a habit can remind (Supabase migration 0022, docs/reminders.md). remind_at is a local time of
-- day, 'HH:MM:SS', or null for no reminder, which is how every habit from before reads.

ALTER TABLE habits ADD COLUMN remind_at TEXT;

-- 0015: a habit's day can fail (Supabase migration 0021, docs/habits.md). failed 1 misses the period at
-- once, today included. The default keeps every check-in from before as it was.

ALTER TABLE habit_checkins ADD COLUMN failed INTEGER NOT NULL DEFAULT 0;

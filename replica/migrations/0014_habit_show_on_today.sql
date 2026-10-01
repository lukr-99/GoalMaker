-- 0014: a habit can stay off Today (Supabase migration 0020, docs/habits.md). show_on_today 0 keeps a
-- habit off Today's ring row and the widgets; it is still on the Habits page and counts everywhere
-- else. The default keeps every habit from before on Today.

ALTER TABLE habits ADD COLUMN show_on_today INTEGER NOT NULL DEFAULT 1;

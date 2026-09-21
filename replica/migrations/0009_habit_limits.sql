-- 0009: habits you want to keep down (Supabase migration 0014, docs/habits.md).
--
-- 'at_most' makes a habit's target a limit rather than something to reach. The column has a default,
-- like the rest of the replica, so a row from an older device is a habit to build.

ALTER TABLE habits ADD COLUMN direction TEXT NOT NULL DEFAULT 'at_least'
    CHECK (direction IN ('at_least', 'at_most'));

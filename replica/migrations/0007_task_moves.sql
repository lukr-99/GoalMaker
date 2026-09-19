-- 0007: how often a task was moved (Supabase migration 0012, docs/reviews.md).
--
-- Nullable with a default, like the rest of the replica: a row that arrives without the column (an
-- older device, a partial write) counts as never moved.

ALTER TABLE tasks ADD COLUMN moved_count INTEGER DEFAULT 0;

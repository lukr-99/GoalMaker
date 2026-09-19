-- 0006: the answers written in a review (Supabase migration 0011, docs/reviews.md).
--
-- JSON columns are stored as their text in the replica; the apps parse them by the column's kind in
-- contracts/schemas/synced-tables.json.

ALTER TABLE reviews ADD COLUMN reflections TEXT NOT NULL DEFAULT '[]';

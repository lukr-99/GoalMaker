-- 0020: short ids for project items (Supabase migration 0027, docs/projects.md, "Item ids").
--
-- A project may have a key like GM, and each of its items a number, so an item reads GM-12. The
-- server gives the numbers, so a row added on this device has none until it comes back from a sync.

ALTER TABLE projects ADD COLUMN item_key TEXT;
ALTER TABLE tasks ADD COLUMN item_number INTEGER;

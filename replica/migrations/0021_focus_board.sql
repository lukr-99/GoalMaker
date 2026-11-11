-- 0021: Focus Board - manual priority list of tasks (Supabase migration 0029).
--
-- A task on the Focus Board has a position in it; a task off the board has null. The owner drags
-- tasks up and down to reprioritize; the fractional real keeps space between items without a full
-- renumber, the same way 'position' is used in other tables.

ALTER TABLE tasks ADD COLUMN focus_position REAL;

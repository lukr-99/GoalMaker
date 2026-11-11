-- 0029: Focus Board - manual priority list of tasks (replica migration 0021).
--
-- A task on the Focus Board has a position in it; a task off the board has null. The owner drags
-- tasks up and down to reprioritize; the fractional real keeps space between items without a full
-- renumber, the same way 'position' is used in other tables.

alter table public.tasks add column focus_position real;

create index tasks_focus_position_idx
  on public.tasks (owner_id, focus_position)
  where focus_position is not null;

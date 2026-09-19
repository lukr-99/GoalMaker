-- 0012: how often a task was moved (spec, story 63; M4-06).
--
-- A review asks about tasks that keep sliding, and the apps work offline, so the count lives on the
-- task rather than being read back from the activity log. It goes up by one whenever a planned task
-- is planned for another day (docs/reviews.md, contracts/vectors/plan.json).

alter table public.tasks
  add column moved_count integer not null default 0 check (moved_count >= 0);

comment on column public.tasks.moved_count is 'How often this task was moved from one planned day to another.';

-- 0015: who made a task (docs/projects.md, "Who made an item").
--
-- A task says whether the owner made it or Claude did, so a project board can show the owner's items
-- apart from Claude's. It is set once, when the row is created, and never changes after: an edit, an
-- undo or a repeated sync push can't rewrite it.
--
-- A new row that says who made it keeps that. The apps say the owner; the connector says the owner
-- when the owner asked for that exact item. A row that doesn't say (an app from before this column,
-- or the connector's default) takes it from the same x-goalmaker-actor header the activity log reads
-- (0004): Claude when the connector sent it, the owner otherwise. The column has no default, so a
-- missing value reaches the trigger as null.
--
-- Tasks made before this migration are filled in from the activity log's 'create' entries, which
-- still reach back to the first task. The fill is not logged as a change of its own; the stamp
-- trigger moves updated_at, which is what carries the new value to the replicas.

alter table public.tasks add column made_by text not null default 'owner'
  check (made_by in ('owner', 'claude'));

alter table public.tasks disable trigger tasks_log;
update public.tasks t
set made_by = 'claude'
where exists (
  select 1 from public.activity_log a
  where a.entity = 'tasks' and a.entity_id = t.id and a.action = 'create' and a.actor = 'claude');
alter table public.tasks enable trigger tasks_log;

alter table public.tasks alter column made_by drop default;

create function public.stamp_task_maker()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  requested text := coalesce(current_setting('request.headers', true), '{}')::json ->> 'x-goalmaker-actor';
begin
  if tg_op = 'INSERT' then
    new.made_by := coalesce(new.made_by, case when requested = 'claude' then 'claude' else 'owner' end);
  else
    -- Who made a task is history, like its creation time.
    new.made_by := old.made_by;
  end if;
  return new;
end;
$$;

revoke execute on function public.stamp_task_maker() from public, anon, authenticated;

create trigger tasks_made_by before insert or update on public.tasks
  for each row execute function public.stamp_task_maker();

comment on column public.tasks.made_by is
  'Who made the task, owner or claude; set once when the row is created and never changed.';

-- 0013: projects and their boards (spec: stories 43 to 50 and the data model; M5-01).
--
-- A project is a piece of work with a home of its own: a name, a description, an area, a status, the
-- repository and the folder it lives in, and notes (docs/projects.md). Its items are ordinary tasks
-- with project columns on them, so a project item with a planned day turns up in Today next to
-- everything else, keeps its area, tags, steps and reminders, and repeats like any task.
--
-- An item is typed task, idea or bug, sits in one of the four board columns (backlog, todo, doing,
-- done), carries a priority, and may belong to one of the project's milestones. A task with no
-- project has neither a column nor a milestone, which the check below keeps true.

create table public.projects (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 120),
  description text not null default '' check (char_length(description) <= 2000),
  area_id uuid,
  status text not null default 'active' check (status in ('active', 'paused', 'done')),
  repository_url text check (repository_url is null or char_length(repository_url) between 1 and 500),
  local_folder text check (local_folder is null or char_length(local_folder) between 1 and 500),
  notes text not null default '' check (char_length(notes) <= 20000),
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  foreign key (area_id, owner_id) references public.areas (id, owner_id) on delete set null (area_id)
);

comment on table public.projects is 'A project with its own board: where code work and other long-running work lives.';

create table public.project_milestones (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  project_id uuid not null,
  name text not null check (char_length(name) between 1 and 120),
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  unique (project_id, id),
  foreign key (project_id, owner_id) references public.projects (id, owner_id) on delete cascade
);

comment on table public.project_milestones is 'An optional grouping of a project''s items, like M0 to M6.';

alter table public.tasks add column project_id uuid;
alter table public.tasks add column item_type text not null default 'task'
  check (item_type in ('task', 'idea', 'bug'));
alter table public.tasks add column board_column text
  check (board_column is null or board_column in ('backlog', 'todo', 'doing', 'done'));
alter table public.tasks add column priority text not null default 'normal'
  check (priority in ('low', 'normal', 'high', 'urgent'));
alter table public.tasks add column milestone_id uuid;

alter table public.tasks
  add constraint tasks_project_fkey foreign key (project_id, owner_id) references public.projects (id, owner_id);
alter table public.tasks
  add constraint tasks_milestone_fkey foreign key (project_id, milestone_id)
  references public.project_milestones (project_id, id) on delete set null (milestone_id);
alter table public.tasks
  add constraint tasks_board_column_belongs_to_a_project check ((project_id is null) = (board_column is null));

-- A project that goes leaves its items behind as plain tasks. Postgres can only null the columns of
-- the foreign key itself, and an item with no project may not keep a column, so this clears all three
-- before the row goes (the nightly purge is what deletes a project for real).
create or replace function public.clear_project_items()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.tasks
  set project_id = null, board_column = null, milestone_id = null
  where project_id = old.id;
  return old;
end;
$$;

create trigger projects_clear_items before delete on public.projects
  for each row execute function public.clear_project_items();

comment on column public.tasks.project_id is 'The project this task is an item of, if any.';
comment on column public.tasks.item_type is 'A project item is a task, an idea or a bug; ideas land in the backlog.';
comment on column public.tasks.board_column is 'Which board column a project item sits in; null for a task outside a project.';
comment on column public.tasks.priority is 'low, normal, high or urgent, on every task.';
comment on column public.tasks.milestone_id is 'One of the project''s milestones, if it has any.';

-- The same wiring as every synced table.
do $$
declare
  synced text;
begin
  foreach synced in array array['projects', 'project_milestones'] loop
    execute format(
      'create trigger %1$s_stamp before insert or update on public.%1$I
         for each row execute function public.stamp_synced_row()', synced);
    execute format(
      'create index %1$s_sync_idx on public.%1$I (owner_id, updated_at, id)', synced);
    execute format('alter table public.%I enable row level security', synced);
    execute format(
      'create policy "%1$s: owner reads" on public.%1$I for select to authenticated
         using ((select auth.uid()) = owner_id)', synced);
    execute format(
      'create policy "%1$s: owner inserts" on public.%1$I for insert to authenticated
         with check ((select auth.uid()) = owner_id)', synced);
    execute format(
      'create policy "%1$s: owner updates" on public.%1$I for update to authenticated
         using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id)', synced);
    execute format('revoke all on public.%I from anon, authenticated', synced);
    execute format('grant select, insert, update on public.%I to authenticated', synced);
    execute format('alter publication supabase_realtime add table public.%I', synced);
    execute format(
      'create trigger %1$s_log after insert or update on public.%1$I
         for each row execute function public.log_activity()', synced);
  end loop;
end;
$$;

create index projects_area_idx on public.projects (area_id);
create index project_milestones_project_idx on public.project_milestones (project_id, position);
create index tasks_project_idx on public.tasks (project_id, board_column, position);

-- The nightly purge (0005, 0009, 0010) also clears old project tombstones; tasks go first, and the
-- trigger above leaves the items of a purged project behind as plain tasks.
create or replace function public.purge_tombstones(keep interval default interval '90 days')
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  cutoff timestamptz := now() - keep;
  removed integer := 0;
  step integer;
begin
  -- Children first; the cascades would catch them anyway, but this keeps the count honest.
  delete from public.task_tags where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.task_steps where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.reminders where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.tasks where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.project_milestones where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.projects where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.habit_checkins where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.habit_pauses where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.habits where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.goal_entries where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.goals where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.tags where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.areas where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.ritual_runs where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.reviews where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.activity_log where created_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  return removed;
end;
$$;

revoke execute on function public.purge_tombstones(interval) from public, anon, authenticated;

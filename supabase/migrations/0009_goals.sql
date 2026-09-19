-- 0009: goals (spec: stories 27 to 35 and the data model; M4-01).
--
-- A goal belongs to a period: a year, a month, a week (from its Monday) or a day, named by the
-- period's first day. It measures progress as done or not, as the share of its linked tasks that are
-- done, or as a number against a target (docs/goals.md, contracts/vectors/goals.json). A goal may
-- serve a parent goal of a longer period; the apps check that the parent's period overlaps the
-- child's. parent_id is a plain id rather than a foreign key, like tasks.series_id, so a sync that
-- brings a child before its parent can't be refused.
--
-- goal_entries are amounts logged by hand on a numeric goal ("+5 km"). tasks.goal_id links a task to
-- the goal it serves.

create table public.goals (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 200),
  emoji text check (emoji is null or char_length(emoji) <= 16),
  horizon text not null check (horizon in ('year', 'month', 'week', 'day')),
  period_start date not null,
  parent_id uuid,
  progress_mode text not null default 'done' check (progress_mode in ('done', 'tasks', 'number')),
  target double precision check (target is null or target > 0),
  unit text check (unit is null or char_length(unit) between 1 and 20),
  status text not null default 'open' check (status in ('open', 'done', 'dropped')),
  completed_at timestamptz,
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  constraint goals_period_starts_its_period check (
    horizon = 'day'
    or (horizon = 'week' and extract(isodow from period_start) = 1)
    or (horizon = 'month' and extract(day from period_start) = 1)
    or (horizon = 'year' and extract(month from period_start) = 1 and extract(day from period_start) = 1)),
  constraint goals_number_has_a_target check (progress_mode <> 'number' or target is not null),
  constraint goals_completion_matches_status check ((status = 'done') = (completed_at is not null)),
  constraint goals_not_its_own_parent check (parent_id is null or parent_id <> id)
);

comment on table public.goals is 'Goals for a year, month, week or day, with an optional parent goal of a longer period.';
comment on column public.goals.period_start is 'January 1, the first of the month, the Monday of the week, or the day.';
comment on column public.goals.parent_id is 'The goal this one serves; checked by the apps, not a foreign key (see the file header).';

create table public.goal_entries (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  goal_id uuid not null,
  day date not null,
  amount double precision not null check (amount <> 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  foreign key (goal_id, owner_id) references public.goals (id, owner_id) on delete cascade
);

comment on table public.goal_entries is 'Amounts logged by hand on a numeric goal; negative to correct one.';

alter table public.tasks add column goal_id uuid;
alter table public.tasks
  add constraint tasks_goal_fkey foreign key (goal_id, owner_id) references public.goals (id, owner_id)
  on delete set null (goal_id);

comment on column public.tasks.goal_id is 'The goal this task serves, if any.';

-- The same wiring as every synced table.
do $$
declare
  synced text;
begin
  foreach synced in array array['goals', 'goal_entries'] loop
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

create index goals_period_idx on public.goals (owner_id, horizon, period_start);
create index goals_parent_idx on public.goals (parent_id);
create index goal_entries_goal_idx on public.goal_entries (goal_id);
create index tasks_goal_idx on public.tasks (goal_id);

-- The nightly purge (0005) also clears old goal tombstones; entries go before their goals.
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

-- Undo (0007, 0008) now covers every table the activity log watches, found by its log trigger, so a
-- new synced table needs no change here.
create or replace function public.undo_activity(entry_id bigint)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  entry public.activity_log;
  current_row jsonb;
  columns text;
begin
  select * into entry from public.activity_log where id = entry_id for update;
  if not found or entry.owner_id is distinct from auth.uid() then
    raise exception 'no such change' using errcode = 'P0002';
  end if;
  if entry.undone_at is not null then
    raise exception 'already undone' using errcode = '55000';
  end if;
  if not exists (
    select 1 from pg_catalog.pg_trigger t
    join pg_catalog.pg_class c on c.oid = t.tgrelid
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = entry.entity and t.tgname = entry.entity || '_log'
  ) then
    raise exception 'this change can''t be undone' using errcode = '22023';
  end if;

  execute format('select to_jsonb(t) from public.%I t where t.id = $1', entry.entity)
    into current_row using entry.entity_id;
  if current_row is null or (current_row - 'updated_at') <> (entry.after - 'updated_at') then
    raise exception 'changed since' using errcode = '40001';
  end if;

  if entry.action = 'create' then
    execute format('update public.%I set deleted_at = now() where id = $1', entry.entity)
      using entry.entity_id;
  else
    -- Every column the owner can change goes back to the entry's before snapshot.
    select string_agg(format('%I = r.%I', c.column_name, c.column_name), ', ')
    into columns
    from information_schema.columns c
    where c.table_schema = 'public' and c.table_name = entry.entity
      and c.column_name not in ('id', 'owner_id', 'created_at', 'updated_at');
    execute format(
      'update public.%I t set %s from jsonb_populate_record(null::public.%I, $1) r where t.id = $2',
      entry.entity, columns, entry.entity)
      using entry.before, entry.entity_id;
  end if;

  update public.activity_log set undone_at = now() where id = entry.id;
end;
$$;

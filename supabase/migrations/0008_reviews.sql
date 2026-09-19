-- 0008: reviews (spec: data model and story 75; M3-09).
--
-- One review per kind and period: a week starting on its Monday, a month on its first day, a year
-- on January 1. The connector saves the summary a Claude routine writes (docs/connector.md); the
-- apps' review screens arrive in M4, which also adds the reflections. Devices and the connector name
-- a review by a UUID version 5 of 'review/<owner>/<kind>/<period start>' (contracts/vectors/reviews.json),
-- so everyone writing the same review writes the same row.

create table public.reviews (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind text not null check (kind in ('weekly', 'monthly', 'yearly')),
  period_start date not null,
  mood smallint check (mood between 1 and 5),
  energy smallint check (energy between 1 and 5),
  summary text not null default '' check (char_length(summary) <= 20000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (owner_id, kind, period_start),
  constraint reviews_period_starts_its_period check (
    (kind = 'weekly' and extract(isodow from period_start) = 1)
    or (kind = 'monthly' and extract(day from period_start) = 1)
    or (kind = 'yearly' and extract(month from period_start) = 1 and extract(day from period_start) = 1))
);

comment on table public.reviews is 'Weekly, monthly and yearly reviews: mood, energy and a summary per period.';
comment on column public.reviews.period_start is 'Monday of the week, the first of the month, or January 1.';

-- The same wiring as every synced table.
create trigger reviews_stamp before insert or update on public.reviews
  for each row execute function public.stamp_synced_row();
create index reviews_sync_idx on public.reviews (owner_id, updated_at, id);
alter table public.reviews enable row level security;
create policy "reviews: owner reads" on public.reviews for select to authenticated
  using ((select auth.uid()) = owner_id);
create policy "reviews: owner inserts" on public.reviews for insert to authenticated
  with check ((select auth.uid()) = owner_id);
create policy "reviews: owner updates" on public.reviews for update to authenticated
  using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
revoke all on public.reviews from anon, authenticated;
grant select, insert, update on public.reviews to authenticated;
alter publication supabase_realtime add table public.reviews;
create trigger reviews_log after insert or update on public.reviews
  for each row execute function public.log_activity();

-- The nightly purge (0005) also clears old review tombstones.
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

-- Undo (0007) covers reviews too.
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
  if entry.entity not in ('areas', 'tags', 'tasks', 'task_steps', 'task_tags', 'reminders', 'ritual_runs', 'reviews') then
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

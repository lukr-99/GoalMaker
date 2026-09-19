-- 0006: archived areas, and a record of the rituals that ran (docs/reminders.md, M2-09 and M2-11).
--
-- An archived area leaves the pickers and the filters but keeps its tasks, its color and its place;
-- archived_at says when, and clearing it brings the area back.
--
-- ritual_runs records that a ritual was done or skipped for a planning day, on any device, so the
-- evening Plan tomorrow reminder stays quiet on the other device too. Devices name a run by a UUID
-- version 5 of '<owner>/<ritual>/<day>' (docs/reminders.md), so two devices recording the same run
-- make the same row and sync merges them; the unique key guards against anything else.

alter table public.areas add column archived_at timestamptz;

comment on column public.areas.archived_at is
  'When the area was archived (hidden from pickers and filters, tasks kept); null while in use.';

create table public.ritual_runs (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  ritual text not null check (ritual in ('plan_tomorrow', 'weekly_review', 'monthly_review')),
  day date not null,
  outcome text not null default 'done' check (outcome in ('done', 'skipped')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (owner_id, ritual, day)
);

comment on table public.ritual_runs is
  'One row per ritual and planning day that was done or skipped, from any device.';
comment on column public.ritual_runs.day is
  'The planning day the ritual belongs to (docs/lists.md), not the calendar date it ran on.';

-- The same wiring as every synced table in 0003 and 0004.
create trigger ritual_runs_stamp before insert or update on public.ritual_runs
  for each row execute function public.stamp_synced_row();
create index ritual_runs_sync_idx on public.ritual_runs (owner_id, updated_at, id);
alter table public.ritual_runs enable row level security;
create policy "ritual_runs: owner reads" on public.ritual_runs for select to authenticated
  using ((select auth.uid()) = owner_id);
create policy "ritual_runs: owner inserts" on public.ritual_runs for insert to authenticated
  with check ((select auth.uid()) = owner_id);
create policy "ritual_runs: owner updates" on public.ritual_runs for update to authenticated
  using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
revoke all on public.ritual_runs from anon, authenticated;
grant select, insert, update on public.ritual_runs to authenticated;
alter publication supabase_realtime add table public.ritual_runs;
create trigger ritual_runs_log after insert or update on public.ritual_runs
  for each row execute function public.log_activity();

-- The nightly purge (0005) also clears old ritual tombstones.
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
  delete from public.activity_log where created_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  return removed;
end;
$$;

revoke execute on function public.purge_tombstones(interval) from public, anon, authenticated;

-- 0026: calendar events (spec: stories 120 to 123; docs/calendar.md; M10-01).
--
-- An event is something that takes up days rather than gets done: a trip, a holiday, a conference.
-- It has a title, a first and a last day, and maybe notes and an area. It is not ticked off; it shows
-- on every one of its days. Times inside a day, repeats and reminders are not part of it (M10).

create table public.events (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 200),
  starts_on date not null,
  ends_on date not null,
  notes text check (char_length(notes) <= 10000),
  area_id uuid,
  made_by text not null check (made_by in ('owner', 'claude')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  foreign key (area_id, owner_id) references public.areas (id, owner_id) on delete set null (area_id),
  constraint events_end_not_before_start check (ends_on >= starts_on),
  constraint events_at_most_a_year check (ends_on - starts_on <= 366)
);

comment on table public.events is 'Something that takes up days, from starts_on to ends_on; not ticked off.';
comment on column public.events.starts_on is 'The first day of the event.';
comment on column public.events.ends_on is 'The last day of the event, on or after the first and at most 366 days later. A one-day event has both the same.';
comment on column public.events.notes is 'Free notes; optional.';
comment on column public.events.made_by is 'Who added the event, owner or claude; set once, like a task''s.';

-- Who added an event is history, like a task's maker: the same rule, the same function (0015).
create trigger events_made_by before insert or update on public.events
  for each row execute function public.stamp_task_maker();

-- The same wiring as every synced table. Undo (0009) finds the table by its log trigger.
do $$
declare
  synced text;
begin
  foreach synced in array array['events'] loop
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

create index events_owner_start_idx on public.events (owner_id, starts_on);
create index events_area_idx on public.events (area_id);

-- The nightly purge (0005 to 0023) also clears old event tombstones.
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
  delete from public.tally_days where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.tally_rules where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.tally_categories where deleted_at < cutoff;
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
  delete from public.life_goal_pictures where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.life_goals where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.events where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.wants where deleted_at < cutoff;
  get diagnostics step = row_count; removed := removed + step;
  delete from public.want_cooldowns where deleted_at < cutoff;
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

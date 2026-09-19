-- 0010: habits (spec: stories 36 to 42 and the data model; M4-03).
--
-- A habit runs daily, on chosen weekdays (a bitmask: Monday 1, Tuesday 2 ... Sunday 64), or N times a
-- week or a month, and is measured as a check, a count or an amount against a target (docs/habits.md,
-- contracts/vectors/habits.json). It may serve a goal: check-ins of a habit in the goal's unit count
-- toward a numeric goal.
--
-- A day has at most one check-in per habit, holding the day's value or marking its period skipped.
-- Its id is a name-based UUID of the habit and the day, so two devices checking in on the same day
-- write the same row. Pauses are ranges of days that neither break a streak nor count; they stay after
-- the habit resumes, so old streaks still read right.

create table public.habits (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 100),
  emoji text check (emoji is null or char_length(emoji) <= 16),
  cadence text not null default 'daily' check (cadence in ('daily', 'weekdays', 'per_week', 'per_month')),
  weekdays integer,
  times integer,
  measure text not null default 'check' check (measure in ('check', 'count', 'amount')),
  target double precision check (target is null or target > 0),
  unit text check (unit is null or char_length(unit) between 1 and 20),
  goal_id uuid,
  starts_on date not null,
  archived_at timestamptz,
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  constraint habits_weekdays_fit_the_cadence check (
    (cadence = 'weekdays') = (weekdays is not null) and (weekdays is null or weekdays between 1 and 127)),
  constraint habits_times_fit_the_cadence check (
    (cadence in ('per_week', 'per_month')) = (times is not null)
    and (times is null or times >= 1)
    and (cadence <> 'per_week' or times <= 7)
    and (cadence <> 'per_month' or times <= 31)),
  constraint habits_only_counts_have_a_target check ((measure = 'check') = (target is null)),
  foreign key (goal_id, owner_id) references public.goals (id, owner_id) on delete set null (goal_id)
);

comment on table public.habits is 'Habits: a cadence, a measure against a target, and the goal they may serve.';
comment on column public.habits.weekdays is 'For the weekdays cadence: Monday 1, Tuesday 2, Wednesday 4 ... Sunday 64.';
comment on column public.habits.times is 'For per_week (1 to 7) and per_month (1 to 31): how many days a period needs.';
comment on column public.habits.starts_on is 'Streaks never reach back before this day.';

create table public.habit_checkins (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  habit_id uuid not null,
  day date not null,
  value double precision not null default 0 check (value >= 0),
  skipped boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (habit_id, day),
  foreign key (habit_id, owner_id) references public.habits (id, owner_id) on delete cascade
);

comment on table public.habit_checkins is 'A day''s one check-in per habit (a name-based id of habit and day): its value, or its period skipped.';

create table public.habit_pauses (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  habit_id uuid not null,
  starts_on date not null,
  ends_on date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint habit_pauses_end_after_they_start check (ends_on is null or ends_on >= starts_on),
  foreign key (habit_id, owner_id) references public.habits (id, owner_id) on delete cascade
);

comment on table public.habit_pauses is 'Days a habit rests: they neither break its streak nor count. ends_on null while the pause lasts.';

-- The same wiring as every synced table.
do $$
declare
  synced text;
begin
  foreach synced in array array['habits', 'habit_checkins', 'habit_pauses'] loop
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

create index habits_goal_idx on public.habits (goal_id);
create index habit_checkins_habit_idx on public.habit_checkins (habit_id, day);
create index habit_pauses_habit_idx on public.habit_pauses (habit_id);

-- The nightly purge (0005, 0009) also clears old habit tombstones; check-ins and pauses go first.
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

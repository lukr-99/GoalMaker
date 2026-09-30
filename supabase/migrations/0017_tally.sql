-- 0017: Tally, where time went on the phone and the PC (spec: stories 109 to 113; docs/tally.md;
-- ADR 0013; M8-10).
--
-- The raw record of which app or window was in front never leaves the device. What syncs is one row
-- per planning day, device, category and project with its minutes, so no column here can hold an
-- app or a window's name; the only such text is a rule's pattern, which the owner wrote. A device
-- rewrites its own days, so tally_days skips the activity log: undo is for things the owner did.
--
-- A category is either a default's key from contracts/content/tally-rules.json (coding, video, ...)
-- or the id of one of the owner's own tally_categories, so both are kept as text. A tally day's id is
-- a UUID version 5 of 'tally/<owner>/<day>/<device>/<category>/<project or ->'
-- (contracts/vectors/tally.json), so a device rewriting a day replaces its own rows and never
-- another device's.

create table public.tally_categories (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 40),
  color text not null default 'violet' check (color ~ '^[a-z][a-z0-9-]{0,23}$'),
  emoji text check (emoji is null or char_length(emoji) between 1 and 16),
  position integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id)
);

comment on table public.tally_categories is 'The owner''s own Tally categories, beside the defaults the apps ship.';

create table public.tally_rules (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  match text not null check (match in ('app', 'title', 'folder')),
  pattern text not null check (char_length(btrim(pattern)) between 1 and 200),
  platform text not null default 'any' check (platform in ('android', 'windows', 'any')),
  category text not null check (char_length(category) between 1 and 60),
  project_id uuid,
  position integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  foreign key (project_id, owner_id) references public.projects (id, owner_id) on delete set null (project_id),
  constraint tally_rules_titles_are_windows check (match = 'app' or platform <> 'android')
);

comment on table public.tally_rules is 'The owner''s own sorting rules: an app, a window title or an editor folder to a category.';
comment on column public.tally_rules.pattern is 'What the owner wrote to match: the only app or window text Tally keeps.';

create table public.tally_days (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day date not null,
  device uuid not null,
  device_kind text not null check (device_kind in ('phone', 'pc')),
  category text not null check (char_length(category) between 1 and 60),
  project_id uuid,
  minutes integer not null check (minutes between 0 and 1440),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  foreign key (project_id, owner_id) references public.projects (id, owner_id) on delete set null (project_id),
  constraint tally_days_phones_have_no_projects check (device_kind = 'pc' or project_id is null)
);

comment on table public.tally_days is 'Minutes per planning day, device, category and project; never the apps or windows themselves.';
comment on column public.tally_days.device is 'A random id each install makes once; device_kind says phone or pc.';

-- The same wiring as every synced table; only the owner's own words go through the activity log.
do $$
declare
  synced text;
begin
  foreach synced in array array['tally_categories', 'tally_rules', 'tally_days'] loop
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
  end loop;
  foreach synced in array array['tally_categories', 'tally_rules'] loop
    execute format(
      'create trigger %1$s_log after insert or update on public.%1$I
         for each row execute function public.log_activity()', synced);
  end loop;
end;
$$;

create index tally_days_day_idx on public.tally_days (owner_id, day);
create index tally_days_project_idx on public.tally_days (project_id);
create index tally_rules_project_idx on public.tally_rules (project_id);

-- The nightly purge (0005, 0009, 0010, 0013, 0016) also clears old Tally tombstones.
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

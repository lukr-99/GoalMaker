-- 0021: life goals and their pictures (spec: stories 114 to 119; docs/life-goals.md; ADR 0018; M9-01).
--
-- A life goal is something the owner wants in their life in the long run, with why it matters, an
-- optional by date and pictures. It stands outside the goal cascade: no horizon, period or pace. It is
-- open, achieved or dropped, with the time it closed.
--
-- A picture is a row here and a JPEG in the private bucket life-goal-pictures, at
-- '<owner id>/<picture id>.jpg'. The apps shrink it before it goes up and remove the file when the row
-- goes; the JSON backup carries the rows only.

create table public.life_goals (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 200),
  why text not null check (char_length(why) between 1 and 2000),
  by_date date,
  area_id uuid,
  status text not null default 'open' check (status in ('open', 'achieved', 'dropped')),
  closed_at timestamptz,
  position double precision not null default 0,
  made_by text not null check (made_by in ('owner', 'claude')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  foreign key (area_id, owner_id) references public.areas (id, owner_id) on delete set null (area_id),
  constraint life_goals_closed_with_a_time check ((status = 'open') = (closed_at is null))
);

comment on table public.life_goals is 'What the owner wants in their life in the long run, with why it matters; outside the goal cascade.';
comment on column public.life_goals.why is 'Why it matters; the why reminder shows it.';
comment on column public.life_goals.by_date is 'The day the owner means to have it by; optional.';
comment on column public.life_goals.closed_at is 'When it was achieved or dropped; null while it is open. Reopening clears it.';
comment on column public.life_goals.made_by is 'Who added the life goal, owner or claude; set once, like a task''s.';

create table public.life_goal_pictures (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  life_goal_id uuid not null,
  position double precision not null default 0,
  width integer not null check (width between 1 and 4096),
  height integer not null check (height between 1 and 4096),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  foreign key (life_goal_id, owner_id) references public.life_goals (id, owner_id) on delete cascade
);

comment on table public.life_goal_pictures is 'A picture of a life goal; the file is <owner id>/<id>.jpg in the life-goal-pictures bucket.';

-- Who added a life goal is history, like a task's maker: the same rule, the same function (0015).
create trigger life_goals_made_by before insert or update on public.life_goals
  for each row execute function public.stamp_task_maker();

-- The same wiring as every synced table.
do $$
declare
  synced text;
begin
  foreach synced in array array['life_goals', 'life_goal_pictures'] loop
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

create index life_goals_area_idx on public.life_goals (area_id);
create index life_goal_pictures_goal_idx on public.life_goal_pictures (life_goal_id);

-- The pictures' files: private, JPEG only, 2 MB each, every owner in a folder named by their id.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('life-goal-pictures', 'life-goal-pictures', false, 2097152, array['image/jpeg'])
on conflict (id) do nothing;

create policy "life-goal-pictures: owner reads"
  on storage.objects for select to authenticated
  using (bucket_id = 'life-goal-pictures' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "life-goal-pictures: owner uploads"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'life-goal-pictures' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "life-goal-pictures: owner replaces"
  on storage.objects for update to authenticated
  using (bucket_id = 'life-goal-pictures' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'life-goal-pictures' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "life-goal-pictures: owner removes"
  on storage.objects for delete to authenticated
  using (bucket_id = 'life-goal-pictures' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- The nightly purge (0005 to 0017) also clears old life goal tombstones, pictures first.
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

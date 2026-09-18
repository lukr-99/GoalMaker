-- 0003: the planning core that both apps replicate (docs/sync.md).
--
-- Every synced table has a device-made UUID, the owner, the device's created_at, a server-stamped
-- updated_at (the sync clock) and a deleted_at tombstone. Devices never hard-delete; a scheduled job
-- purges old tombstones (0005). Row security allows only the owner; there is no delete policy.

create function public.stamp_synced_row()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    new.owner_id := coalesce(new.owner_id, auth.uid());
  else
    -- A row never changes hands, and its creation time is history.
    new.owner_id := old.owner_id;
    new.created_at := old.created_at;
  end if;
  new.updated_at := now();
  return new;
end;
$$;

comment on function public.stamp_synced_row() is
  'Server clock for updated_at (the sync watermark) and immutable owner_id/created_at on synced tables.';

-- Areas: colored life areas (Health, School, ...). color is a palette key, not a hex value, so each
-- theme can render it for light and dark.
create table public.areas (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 60),
  color text not null default 'violet' check (color ~ '^[a-z][a-z0-9-]{0,23}$'),
  emoji text check (emoji is null or char_length(emoji) <= 16),
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id)
);

create table public.tags (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 40),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id)
);

create table public.tasks (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 500),
  notes text not null default '' check (char_length(notes) <= 20000),
  planned_date date,
  planned_time time,
  deadline date,
  top_priority boolean not null default false,
  status text not null default 'open' check (status in ('open', 'done', 'dropped')),
  completed_at timestamptz,
  area_id uuid,
  recurrence text check (recurrence is null or char_length(recurrence) <= 200),
  series_id uuid,
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint tasks_time_needs_a_day check (planned_time is null or planned_date is not null),
  constraint tasks_completion_matches_status check ((status = 'done') = (completed_at is not null)),
  unique (id, owner_id),
  foreign key (area_id, owner_id) references public.areas (id, owner_id) on delete set null (area_id)
);

comment on column public.tasks.recurrence is 'RRULE subset (spec: repeat presets), validated by the apps.';

create table public.task_steps (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  title text not null check (char_length(title) between 1 and 300),
  done boolean not null default false,
  position double precision not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  foreign key (task_id, owner_id) references public.tasks (id, owner_id) on delete cascade
);

create table public.task_tags (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  tag_id uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  foreign key (task_id, owner_id) references public.tasks (id, owner_id) on delete cascade,
  foreign key (tag_id, owner_id) references public.tags (id, owner_id) on delete cascade
);

create table public.reminders (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  fire_at timestamptz,
  offset_minutes integer check (offset_minutes is null or offset_minutes between -525600 and 0),
  important boolean not null default false,
  state text not null default 'pending' check (state in ('pending', 'snoozed', 'dismissed', 'done')),
  snoozed_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint reminders_fixed_or_relative check ((fire_at is null) <> (offset_minutes is null)),
  constraint reminders_snooze_has_time check ((state = 'snoozed') = (snoozed_until is not null)),
  foreign key (task_id, owner_id) references public.tasks (id, owner_id) on delete cascade
);

comment on column public.reminders.offset_minutes is
  'Relative reminder: minutes before the task''s planned time (negative or zero).';

-- References use (id, owner_id) so a row can only point at rows of the same owner: foreign key
-- checks bypass row security, so a plain id reference could attach rows to someone else's task.

-- Triggers, indexes, row security and grants are the same for every synced table.
do $$
declare
  synced text;
begin
  foreach synced in array array['areas', 'tags', 'tasks', 'task_steps', 'task_tags', 'reminders'] loop
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
end;
$$;

create index tasks_area_idx on public.tasks (area_id);
create index task_steps_task_idx on public.task_steps (task_id);
create index task_tags_task_idx on public.task_tags (task_id);
create index task_tags_tag_idx on public.task_tags (tag_id);
create index reminders_task_idx on public.reminders (task_id);

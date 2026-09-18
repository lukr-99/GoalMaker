-- 0004: the activity log (spec: every change shows who made it, with undo from M3).
--
-- Written only by the server, from triggers on the synced tables, so it can't be skipped by a client.
-- The actor comes from the x-goalmaker-actor request header (the connector sends "claude");
-- anything else, or no header, means the owner. Owners read their own log; nobody writes it directly.

create table public.activity_log (
  id bigint generated always as identity primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  entity text not null,
  entity_id uuid not null,
  action text not null check (action in ('create', 'update', 'delete', 'restore')),
  actor text not null default 'owner' check (actor in ('owner', 'claude')),
  before jsonb,
  after jsonb not null,
  created_at timestamptz not null default now()
);

create index activity_log_owner_time_idx on public.activity_log (owner_id, created_at desc);
create index activity_log_entity_idx on public.activity_log (entity, entity_id);

alter table public.activity_log enable row level security;

create policy "activity_log: owner reads"
  on public.activity_log for select
  to authenticated
  using ((select auth.uid()) = owner_id);

revoke all on public.activity_log from anon, authenticated;
grant select on public.activity_log to authenticated;

create function public.log_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  requested text := coalesce(current_setting('request.headers', true), '{}')::json ->> 'x-goalmaker-actor';
  actor text := case when requested = 'claude' then 'claude' else 'owner' end;
  action text;
begin
  if tg_op = 'INSERT' then
    action := 'create';
  elsif old.deleted_at is null and new.deleted_at is not null then
    action := 'delete';
  elsif old.deleted_at is not null and new.deleted_at is null then
    action := 'restore';
  elsif (to_jsonb(old) - 'updated_at') = (to_jsonb(new) - 'updated_at') then
    -- A repeated push of an unchanged row is not a change.
    return null;
  else
    action := 'update';
  end if;

  insert into public.activity_log (owner_id, entity, entity_id, action, actor, before, after)
  values (
    new.owner_id,
    tg_table_name,
    new.id,
    action,
    actor,
    case when tg_op = 'INSERT' then null else to_jsonb(old) end,
    to_jsonb(new));
  return null;
end;
$$;

revoke execute on function public.log_activity() from public, anon, authenticated;

do $$
declare
  synced text;
begin
  foreach synced in array array['areas', 'tags', 'tasks', 'task_steps', 'task_tags', 'reminders'] loop
    execute format(
      'create trigger %1$s_log after insert or update on public.%1$I
         for each row execute function public.log_activity()', synced);
  end loop;
end;
$$;

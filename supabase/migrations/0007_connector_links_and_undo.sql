-- 0007: connector links and undo (spec: stories 19, 72 and 77; ADR 0003; M3-01).
--
-- A connector link is a random secret in a URL that Claude uses to reach the connector Edge Function.
-- Only its SHA-256 hash is stored. The owner creates one (which revokes the previous one, so creating
-- is also rotating) and revokes them from either app; the function resolves a hash to its owner,
-- counts the call and refuses a revoked link or one over its per-minute limit. The function then acts
-- as that owner under row security (docs/connector.md), never as the service role on user tables.
--
-- Undo puts a row back the way an activity log entry found it, as the owner, and marks the entry
-- undone. It refuses when the row changed after the entry, so an undo never throws away later work.

create table public.connector_links (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users (id) on delete cascade,
  secret_hash text not null unique check (secret_hash ~ '^[0-9a-f]{64}$'),
  created_at timestamptz not null default now(),
  last_used_at timestamptz,
  revoked_at timestamptz,
  window_started_at timestamptz,
  window_calls integer not null default 0 check (window_calls >= 0)
);

comment on table public.connector_links is
  'Secret links for the Claude connector. Only the SHA-256 hash of a secret is kept.';
comment on column public.connector_links.window_calls is
  'Calls in the minute that started at window_started_at, for the rate limit.';

create index connector_links_owner_idx on public.connector_links (owner_id, created_at desc);

alter table public.connector_links enable row level security;

create policy "connector_links: owner reads"
  on public.connector_links for select
  to authenticated
  using ((select auth.uid()) = owner_id);

-- Owners see when their links were made, used and revoked; the hash and the counters stay server-side.
-- Links are made and revoked only through the functions below.
revoke all on public.connector_links from anon, authenticated;
grant select (id, created_at, last_used_at, revoked_at) on public.connector_links to authenticated;

create function public.create_connector_link()
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  owner uuid := auth.uid();
  secret text;
begin
  if owner is null then
    raise exception 'sign in to create a connector link' using errcode = '42501';
  end if;

  update public.connector_links set revoked_at = now() where owner_id = owner and revoked_at is null;

  -- 32 random bytes, URL-safe base64 without padding: 43 characters.
  secret := rtrim(translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/', '-_'), '=');
  insert into public.connector_links (owner_id, secret_hash)
  values (owner, encode(extensions.digest(secret, 'sha256'), 'hex'));
  return secret;
end;
$$;

comment on function public.create_connector_link() is
  'Makes a new connector secret for the signed-in owner, revokes the previous one, and returns the secret once.';

create function public.revoke_connector_links()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  owner uuid := auth.uid();
  revoked integer;
begin
  if owner is null then
    raise exception 'sign in to revoke connector links' using errcode = '42501';
  end if;

  update public.connector_links set revoked_at = now() where owner_id = owner and revoked_at is null;
  get diagnostics revoked = row_count;
  return revoked;
end;
$$;

comment on function public.revoke_connector_links() is
  'Revokes every active connector link of the signed-in owner; returns how many.';

revoke execute on function public.create_connector_link() from public, anon;
revoke execute on function public.revoke_connector_links() from public, anon;
grant execute on function public.create_connector_link() to authenticated;
grant execute on function public.revoke_connector_links() to authenticated;

-- For the connector Edge Function only. Returns no row for an unknown or revoked link, and
-- allowed = false once the link made more than calls_per_minute calls in the current minute.
create function public.connector_resolve(hash text, calls_per_minute integer default 120)
returns table (owner_id uuid, allowed boolean)
language plpgsql
security definer
set search_path = ''
as $$
declare
  link public.connector_links;
begin
  select * into link from public.connector_links l
  where l.secret_hash = hash and l.revoked_at is null
  for update;
  if not found then
    return;
  end if;

  if link.window_started_at is null or link.window_started_at <= now() - interval '1 minute' then
    link.window_started_at := now();
    link.window_calls := 1;
  else
    link.window_calls := link.window_calls + 1;
  end if;

  update public.connector_links l
  set window_started_at = link.window_started_at,
      window_calls = link.window_calls,
      last_used_at = now()
  where l.id = link.id;

  owner_id := link.owner_id;
  allowed := link.window_calls <= calls_per_minute;
  return next;
end;
$$;

revoke execute on function public.connector_resolve(text, integer) from public, anon, authenticated;
grant execute on function public.connector_resolve(text, integer) to service_role;

-- The activity log learns the system actor and when an entry was undone.
alter table public.activity_log drop constraint activity_log_actor_check;
alter table public.activity_log
  add constraint activity_log_actor_check check (actor in ('owner', 'claude', 'system'));
alter table public.activity_log add column undone_at timestamptz;

comment on column public.activity_log.undone_at is 'When the owner undid this change; null while it stands.';

create function public.undo_activity(entry_id bigint)
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
  if entry.entity not in ('areas', 'tags', 'tasks', 'task_steps', 'task_tags', 'reminders', 'ritual_runs') then
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

comment on function public.undo_activity(bigint) is
  'Puts a row back the way an activity log entry of the signed-in owner found it and marks the entry undone.';

revoke execute on function public.undo_activity(bigint) from public, anon;
grant execute on function public.undo_activity(bigint) to authenticated;

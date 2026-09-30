-- 0019: the quick chat (M7, docs/assistant.md).
--
-- The assistant Edge Function acts as the owner, so a change it makes is logged as the owner's. The
-- activity log now also says which way a change came in: via = 'chat' when the function sent the
-- x-goalmaker-via header, null for everything else. The log is not synced; the apps and the
-- connector read it from the server, so an app from before 0019 simply doesn't show the note.
--
-- The chat's own limits (30 requests a minute and a daily cap per owner) are counted in
-- assistant_usage, one row per owner, which only the function reaches, through assistant_count.
-- Nothing of the conversation itself is kept on the server.

alter table public.activity_log add column via text check (via in ('chat'));

comment on column public.activity_log.via is
  'Which way the change came in: chat for the quick chat, null for the apps, the connector and the server.';

-- The same as 0004, plus via.
create or replace function public.log_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  headers json := coalesce(current_setting('request.headers', true), '{}')::json;
  requested text := headers ->> 'x-goalmaker-actor';
  actor text := case when requested = 'claude' then 'claude' else 'owner' end;
  via text := case when headers ->> 'x-goalmaker-via' = 'chat' then 'chat' end;
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

  insert into public.activity_log (owner_id, entity, entity_id, action, actor, via, before, after)
  values (
    new.owner_id,
    tg_table_name,
    new.id,
    action,
    actor,
    via,
    case when tg_op = 'INSERT' then null else to_jsonb(old) end,
    to_jsonb(new));
  return null;
end;
$$;

revoke execute on function public.log_activity() from public, anon, authenticated;

create table public.assistant_usage (
  owner_id uuid primary key references auth.users (id) on delete cascade,
  minute_started_at timestamptz not null,
  minute_calls integer not null default 0 check (minute_calls >= 0),
  day date not null,
  day_calls integer not null default 0 check (day_calls >= 0)
);

comment on table public.assistant_usage is
  'How many quick chat requests each owner made this minute and this UTC day, for the limits.';

-- Row security on and no policy: no app or connector call reads or writes it.
alter table public.assistant_usage enable row level security;
revoke all on public.assistant_usage from anon, authenticated;

-- For the assistant Edge Function only. Counts one request and answers 'ok', or 'minute' / 'day' when
-- a limit is reached. A refused request is not counted, so a client retrying too fast can't use up
-- the day.
create function public.assistant_count(owner uuid, per_minute integer, per_day integer)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  usage public.assistant_usage;
  today date := (now() at time zone 'UTC')::date;
  verdict text := 'ok';
begin
  insert into public.assistant_usage (owner_id, minute_started_at, day)
  values (owner, now(), today)
  on conflict (owner_id) do nothing;
  select * into usage from public.assistant_usage u where u.owner_id = owner for update;

  if usage.minute_started_at <= now() - interval '1 minute' then
    usage.minute_started_at := now();
    usage.minute_calls := 0;
  end if;
  if usage.day <> today then
    usage.day := today;
    usage.day_calls := 0;
  end if;

  if usage.minute_calls >= per_minute then
    verdict := 'minute';
  elsif usage.day_calls >= per_day then
    verdict := 'day';
  else
    usage.minute_calls := usage.minute_calls + 1;
    usage.day_calls := usage.day_calls + 1;
  end if;

  update public.assistant_usage u
  set minute_started_at = usage.minute_started_at,
      minute_calls = usage.minute_calls,
      day = usage.day,
      day_calls = usage.day_calls
  where u.owner_id = owner;
  return verdict;
end;
$$;

revoke execute on function public.assistant_count(uuid, integer, integer) from public, anon, authenticated;
grant execute on function public.assistant_count(uuid, integer, integer) to service_role;

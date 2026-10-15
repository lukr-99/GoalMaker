-- 0028: the PC signs in by a QR code the phone approves (ADR 0020, docs/sign-in.md, GM-93).
--
-- A PC sign-in is a request that lives two minutes and is used once. It has two random secrets and
-- only their SHA-256 hashes are kept. The scan secret is in the QR code: the signed-in phone sends
-- it to approve or decline, as itself, through the two functions for `authenticated` below. The
-- pick-up secret never leaves the PC: the pc-sign-in Edge Function sends it to pick up an approved
-- request, once, and then asks Supabase Auth for a one-time email code for the owner who approved.
--
-- Against a QR code sent by someone else, the PC shows a two-digit match number and the phone must
-- pick it from three. A wrong pick burns the request. The function calls the start and pick-up
-- functions with a hash of the caller's IP, so one caller can hold only a few open requests and
-- can't fill the table for everyone.
--
-- Nobody but the functions reads or writes the tables: row security is on with no policies, and
-- anon and authenticated have no rights on them.

create table public.pc_sign_ins (
  id uuid primary key default gen_random_uuid(),
  scan_hash text not null unique check (scan_hash ~ '^[0-9a-f]{64}$'),
  pickup_hash text not null check (pickup_hash ~ '^[0-9a-f]{64}$'),
  caller_hash text not null check (caller_hash ~ '^[0-9a-f]{64}$'),
  device text not null check (char_length(device) between 1 and 64),
  match_number smallint not null check (match_number between 10 and 99),
  match_choices smallint[] not null
    check (cardinality(match_choices) = 3 and match_number = any (match_choices)),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  approved_by uuid references auth.users (id) on delete cascade,
  answered_at timestamptz,
  declined boolean not null default false,
  claimed_at timestamptz,
  picked_up_at timestamptz,
  check (expires_at > created_at),
  check (not (declined and approved_by is not null)),
  check ((approved_by is null and not declined) = (answered_at is null))
);

comment on table public.pc_sign_ins is
  'QR sign-in requests from a PC, two minutes long and used once. Only SHA-256 hashes of the secrets are kept.';
comment on column public.pc_sign_ins.device is
  'The name the PC gave. Only a label: anyone can start a request with any name.';
comment on column public.pc_sign_ins.claimed_at is
  'When the function took the approval to make a code. Released when that fails; final once picked_up_at is set.';

create index pc_sign_ins_created_idx on public.pc_sign_ins (created_at);
create index pc_sign_ins_caller_idx on public.pc_sign_ins (caller_hash, expires_at);

-- Calls per caller (a hash of the IP) and action in the current minute.
create table public.pc_sign_in_callers (
  caller_hash text not null check (caller_hash ~ '^[0-9a-f]{64}$'),
  action text not null check (action in ('start', 'pick_up')),
  window_started_at timestamptz not null,
  calls integer not null check (calls >= 0),
  primary key (caller_hash, action)
);

comment on table public.pc_sign_in_callers is
  'Per-minute call counts of the pc-sign-in function, by a SHA-256 hash of the caller''s IP.';

alter table public.pc_sign_ins enable row level security;
alter table public.pc_sign_in_callers enable row level security;
revoke all on public.pc_sign_ins from anon, authenticated;
revoke all on public.pc_sign_in_callers from anon, authenticated;

create function public.pc_sign_in_hash(secret text)
returns text
language sql
immutable
set search_path = ''
as $$
  select encode(extensions.digest(secret, 'sha256'), 'hex');
$$;

-- For the Edge Function only. Counts a call and says whether the caller is still under per_minute.
create function public.pc_sign_in_allow(caller text, kind text, per_minute integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  counted integer;
begin
  insert into public.pc_sign_in_callers as c (caller_hash, action, window_started_at, calls)
  values (caller, kind, now(), 1)
  on conflict (caller_hash, action) do update
  set window_started_at = case when c.window_started_at <= now() - interval '1 minute' then now() else c.window_started_at end,
      calls = case when c.window_started_at <= now() - interval '1 minute' then 1 else c.calls + 1 end
  returning calls into counted;
  return counted <= per_minute;
end;
$$;

-- For the Edge Function only. Makes a request and returns both secrets and the match number once.
-- No row while the caller already holds max_per_caller open requests, or max_open are open in all.
create function public.pc_sign_in_start(
  device text,
  caller text,
  lifetime interval default interval '2 minutes',
  max_open integer default 50,
  max_per_caller integer default 3)
returns table (id uuid, scan_secret text, pickup_secret text, match_number smallint, expires_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
declare
  scan text;
  pickup text;
  choices smallint[];
  made public.pc_sign_ins;
begin
  if (select count(*) from public.pc_sign_ins r
      where r.caller_hash = caller and r.expires_at > now() and r.answered_at is null) >= max_per_caller
    or (select count(*) from public.pc_sign_ins r where r.expires_at > now() and r.answered_at is null) >= max_open then
    return;
  end if;

  -- 32 random bytes each, URL-safe base64 without padding: 43 characters.
  scan := rtrim(translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/', '-_'), '=');
  pickup := rtrim(translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/', '-_'), '=');
  -- Three different two-digit numbers; the first one drawn is the match, the phone sees them sorted.
  select array_agg(n) into choices
  from (select n::smallint from generate_series(10, 99) n order by random() limit 3) drawn;

  insert into public.pc_sign_ins (scan_hash, pickup_hash, caller_hash, device, match_number, match_choices, expires_at)
  values (public.pc_sign_in_hash(scan), public.pc_sign_in_hash(pickup), caller, left(btrim(device), 64),
          choices[1], choices, now() + lifetime)
  returning * into made;

  id := made.id;
  scan_secret := scan;
  pickup_secret := pickup;
  match_number := made.match_number;
  expires_at := made.expires_at;
  return next;
end;
$$;

-- For the signed-in phone: what it is about to approve, with the three numbers to pick from. No row
-- for a request that is unknown, expired or answered.
create function public.pc_sign_in_look(scan_secret text)
returns table (device text, created_at timestamptz, expires_at timestamptz, choices smallint[])
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'sign in to approve a PC' using errcode = '42501';
  end if;

  return query
  select r.device, r.created_at, r.expires_at,
         (select array_agg(c order by c) from unnest(r.match_choices) c)
  from public.pc_sign_ins r
  where r.scan_hash = public.pc_sign_in_hash(scan_secret)
    and r.expires_at > now()
    and r.answered_at is null;
end;
$$;

-- For the signed-in phone: approves as the caller with the number the owner picked, or declines.
-- 'approved', 'declined', 'mismatch' (a wrong number, which burns the request), 'gone' (unknown,
-- expired or answered), or 'no_email' (an owner Auth can't send a code for, so nothing changes).
create function public.pc_sign_in_answer(scan_secret text, approve boolean, picked smallint default null)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  owner uuid := auth.uid();
  found_row public.pc_sign_ins;
begin
  if owner is null then
    raise exception 'sign in to approve a PC' using errcode = '42501';
  end if;

  select * into found_row from public.pc_sign_ins r
  where r.scan_hash = public.pc_sign_in_hash(scan_secret)
    and r.expires_at > now()
    and r.answered_at is null
  for update;
  if not found then
    return 'gone';
  end if;

  if not approve then
    update public.pc_sign_ins r set declined = true, answered_at = now() where r.id = found_row.id;
    return 'declined';
  end if;
  if picked is distinct from found_row.match_number then
    update public.pc_sign_ins r set declined = true, answered_at = now() where r.id = found_row.id;
    return 'mismatch';
  end if;
  if (select coalesce(u.email, '') from auth.users u where u.id = owner) = '' then
    return 'no_email';
  end if;

  update public.pc_sign_ins r set approved_by = owner, answered_at = now() where r.id = found_row.id;
  return 'approved';
end;
$$;

-- For the Edge Function only. What the PC's request is now: waiting, declined, expired, or approved.
-- Approved claims the request for `hold`, so a second call meanwhile reads waiting. The function
-- then makes the code and calls pc_sign_in_finish: made, the request is used up; not made, the claim
-- is let go and the next call gets it again. An approval must be picked up within a minute of the
-- answer. An unknown id or a wrong pick-up secret reads expired, like a request that ran out.
create function public.pc_sign_in_pick_up(request uuid, pickup_secret text, hold interval default interval '30 seconds')
returns table (state text, owner_id uuid, email text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  found_row public.pc_sign_ins;
  address text;
begin
  select * into found_row from public.pc_sign_ins r
  where r.id = request and r.pickup_hash = public.pc_sign_in_hash(pickup_secret)
  for update;

  if not found or found_row.picked_up_at is not null then
    state := 'expired';
  elsif found_row.declined then
    state := 'declined';
  elsif found_row.approved_by is not null and found_row.answered_at > now() - interval '1 minute' then
    address := (select u.email from auth.users u where u.id = found_row.approved_by);
    if coalesce(address, '') = '' or found_row.claimed_at > now() - hold then
      state := 'waiting';
    else
      update public.pc_sign_ins r set claimed_at = now() where r.id = found_row.id;
      state := 'approved';
      owner_id := found_row.approved_by;
      email := address;
    end if;
  elsif found_row.approved_by is null and found_row.expires_at > now() then
    state := 'waiting';
  else
    state := 'expired';
  end if;
  return next;
end;
$$;

-- For the Edge Function only. After a claim: made = true uses the request up, false lets it go.
create function public.pc_sign_in_finish(request uuid, pickup_secret text, made boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.pc_sign_ins r
  set picked_up_at = case when made then now() end,
      claimed_at = case when made then r.claimed_at end
  where r.id = request
    and r.pickup_hash = public.pc_sign_in_hash(pickup_secret)
    and r.claimed_at is not null
    and r.picked_up_at is null;
  return found;
end;
$$;

-- Clears requests older than a day and call counts older than an hour. Run hourly by pg_cron.
create function public.pc_sign_in_purge()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  removed integer;
begin
  delete from public.pc_sign_ins r where r.created_at < now() - interval '1 day';
  get diagnostics removed = row_count;
  delete from public.pc_sign_in_callers c where c.window_started_at < now() - interval '1 hour';
  return removed;
end;
$$;

comment on function public.pc_sign_in_allow(text, text, integer) is
  'Edge Function only: counts a call of a caller and says whether it is under the per-minute limit.';
comment on function public.pc_sign_in_start(text, text, interval, integer, integer) is
  'Edge Function only: starts a PC sign-in and returns its two secrets and match number once.';
comment on function public.pc_sign_in_look(text) is
  'The signed-in phone reads which PC a scanned code belongs to and the numbers to pick from.';
comment on function public.pc_sign_in_answer(text, boolean, smallint) is
  'The signed-in phone approves (as itself, with the picked number) or declines a PC sign-in.';
comment on function public.pc_sign_in_pick_up(uuid, text, interval) is
  'Edge Function only: reads a PC sign-in and claims an approval for making its code.';
comment on function public.pc_sign_in_finish(uuid, text, boolean) is
  'Edge Function only: uses a claimed approval up, or lets it go when no code was made.';
comment on function public.pc_sign_in_purge() is
  'Clears PC sign-ins older than a day and call counts older than an hour. Run hourly by pg_cron.';

revoke execute on function public.pc_sign_in_hash(text) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_allow(text, text, integer) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_start(text, text, interval, integer, integer) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_pick_up(uuid, text, interval) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_finish(uuid, text, boolean) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_purge() from public, anon, authenticated;
revoke execute on function public.pc_sign_in_look(text) from public, anon;
revoke execute on function public.pc_sign_in_answer(text, boolean, smallint) from public, anon;
grant execute on function public.pc_sign_in_look(text) to authenticated;
grant execute on function public.pc_sign_in_answer(text, boolean, smallint) to authenticated;
grant execute on function public.pc_sign_in_allow(text, text, integer) to service_role;
grant execute on function public.pc_sign_in_start(text, text, interval, integer, integer) to service_role;
grant execute on function public.pc_sign_in_pick_up(uuid, text, interval) to service_role;
grant execute on function public.pc_sign_in_finish(uuid, text, boolean) to service_role;

select cron.schedule('goalmaker-purge-pc-sign-ins', '41 * * * *', 'select public.pc_sign_in_purge()');

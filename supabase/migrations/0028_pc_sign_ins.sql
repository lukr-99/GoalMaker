-- 0028: the PC signs in by a QR code the phone approves (ADR 0020, docs/sign-in.md, GM-93).
--
-- A PC sign-in is a request that lives two minutes and is used once. It has two random secrets and
-- only their SHA-256 hashes are kept. The scan secret is in the QR code: the signed-in phone sends
-- it to approve or decline, as itself, through the two functions for `authenticated` below. The
-- pick-up secret never leaves the PC: the pc-sign-in Edge Function sends it to pick up an approved
-- request, once, and then asks Supabase Auth for a one-time email code for the owner who approved.
--
-- Nobody but the functions reads or writes the table: row security is on with no policies, and
-- anon and authenticated have no rights on it.

create table public.pc_sign_ins (
  id uuid primary key default gen_random_uuid(),
  scan_hash text not null unique check (scan_hash ~ '^[0-9a-f]{64}$'),
  pickup_hash text not null check (pickup_hash ~ '^[0-9a-f]{64}$'),
  device text not null check (char_length(device) between 1 and 64),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  approved_by uuid references auth.users (id) on delete cascade,
  answered_at timestamptz,
  declined boolean not null default false,
  picked_up_at timestamptz,
  check (expires_at > created_at),
  check (not (declined and approved_by is not null)),
  check ((approved_by is null and not declined) = (answered_at is null))
);

comment on table public.pc_sign_ins is
  'QR sign-in requests from a PC, two minutes long and used once. Only SHA-256 hashes of the secrets are kept.';
comment on column public.pc_sign_ins.device is
  'The name the PC gave. Only a label: anyone can start a request with any name.';

create index pc_sign_ins_created_idx on public.pc_sign_ins (created_at);

alter table public.pc_sign_ins enable row level security;
revoke all on public.pc_sign_ins from anon, authenticated;

create function public.pc_sign_in_hash(secret text)
returns text
language sql
immutable
set search_path = ''
as $$
  select encode(extensions.digest(secret, 'sha256'), 'hex');
$$;

-- For the Edge Function only. Makes a request and returns both secrets once. Requests older than a
-- day are cleared first. Refuses (no row) while max_open requests are still open, so nobody can fill
-- the table.
create function public.pc_sign_in_start(device text, lifetime interval default interval '2 minutes', max_open integer default 20)
returns table (id uuid, scan_secret text, pickup_secret text, expires_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
declare
  scan text;
  pickup text;
  made public.pc_sign_ins;
begin
  delete from public.pc_sign_ins r where r.created_at < now() - interval '1 day';
  if (select count(*) from public.pc_sign_ins r where r.expires_at > now() and r.picked_up_at is null) >= max_open then
    return;
  end if;

  -- 32 random bytes each, URL-safe base64 without padding: 43 characters.
  scan := rtrim(translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/', '-_'), '=');
  pickup := rtrim(translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/', '-_'), '=');
  insert into public.pc_sign_ins (scan_hash, pickup_hash, device, expires_at)
  values (public.pc_sign_in_hash(scan), public.pc_sign_in_hash(pickup), left(btrim(device), 64), now() + lifetime)
  returning * into made;

  id := made.id;
  scan_secret := scan;
  pickup_secret := pickup;
  expires_at := made.expires_at;
  return next;
end;
$$;

-- For the signed-in phone: what it is about to approve. No row for a request that is unknown,
-- expired, answered or picked up.
create function public.pc_sign_in_look(scan_secret text)
returns table (device text, created_at timestamptz, expires_at timestamptz)
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
  select r.device, r.created_at, r.expires_at
  from public.pc_sign_ins r
  where r.scan_hash = public.pc_sign_in_hash(scan_secret)
    and r.expires_at > now()
    and r.answered_at is null;
end;
$$;

-- For the signed-in phone: approves (as the caller) or declines. True when it answered the request;
-- false when the request is unknown, expired or already answered.
create function public.pc_sign_in_answer(scan_secret text, approve boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  owner uuid := auth.uid();
begin
  if owner is null then
    raise exception 'sign in to approve a PC' using errcode = '42501';
  end if;

  update public.pc_sign_ins r
  set approved_by = case when approve then owner end,
      declined = not approve,
      answered_at = now()
  where r.scan_hash = public.pc_sign_in_hash(scan_secret)
    and r.expires_at > now()
    and r.answered_at is null;
  return found;
end;
$$;

-- For the Edge Function only. What the PC's request is now: waiting, declined, expired, or approved.
-- Approved is answered once: the same statement marks it picked up, so a second call reads expired.
-- An approval must be picked up within a minute of the answer. An unknown id or a wrong pick-up
-- secret reads expired, like a request that ran out, so the answer says nothing about the row.
create function public.pc_sign_in_pick_up(request uuid, pickup_secret text)
returns table (state text, owner_id uuid, email text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  found_row public.pc_sign_ins;
begin
  select * into found_row from public.pc_sign_ins r
  where r.id = request and r.pickup_hash = public.pc_sign_in_hash(pickup_secret)
  for update;

  if not found or found_row.picked_up_at is not null then
    state := 'expired';
  elsif found_row.declined then
    state := 'declined';
  elsif found_row.approved_by is not null and found_row.answered_at > now() - interval '1 minute' then
    update public.pc_sign_ins r set picked_up_at = now() where r.id = found_row.id;
    state := 'approved';
    owner_id := found_row.approved_by;
    email := (select u.email from auth.users u where u.id = found_row.approved_by);
  elsif found_row.approved_by is null and found_row.expires_at > now() then
    state := 'waiting';
  else
    state := 'expired';
  end if;
  return next;
end;
$$;

comment on function public.pc_sign_in_start(text, interval, integer) is
  'Edge Function only: starts a PC sign-in and returns its two secrets once.';
comment on function public.pc_sign_in_look(text) is
  'The signed-in phone reads which PC a scanned code belongs to.';
comment on function public.pc_sign_in_answer(text, boolean) is
  'The signed-in phone approves (as itself) or declines a PC sign-in.';
comment on function public.pc_sign_in_pick_up(uuid, text) is
  'Edge Function only: reads a PC sign-in and picks up an approval once.';

revoke execute on function public.pc_sign_in_hash(text) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_start(text, interval, integer) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_pick_up(uuid, text) from public, anon, authenticated;
revoke execute on function public.pc_sign_in_look(text) from public, anon;
revoke execute on function public.pc_sign_in_answer(text, boolean) from public, anon;
grant execute on function public.pc_sign_in_look(text) to authenticated;
grant execute on function public.pc_sign_in_answer(text, boolean) to authenticated;
grant execute on function public.pc_sign_in_start(text, interval, integer) to service_role;
grant execute on function public.pc_sign_in_pick_up(uuid, text) to service_role;

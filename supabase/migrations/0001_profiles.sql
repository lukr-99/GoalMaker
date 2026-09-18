-- 0001: one profile per signed-in user, owned by that user.
--
-- The profile holds per-user settings that both apps need before any other data exists. It is
-- created by a trigger when Supabase Auth creates the user, so clients never insert it.

create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

comment on function public.set_updated_at() is
  'Stamps updated_at with the server clock. Sync watermarks rely on this, never on device clocks.';

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text check (display_name is null or char_length(display_name) <= 80),
  time_zone text not null default 'Europe/Prague' check (char_length(time_zone) between 1 and 64),
  day_rollover_hour smallint not null default 4 check (day_rollover_hour between 0 and 23),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.profiles is 'Per-user settings. Exactly one row per auth user.';
comment on column public.profiles.day_rollover_hour is
  'Local hour at which "today" becomes the next day (spec: default 04:00).';

create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

alter table public.profiles enable row level security;

create policy "profiles: owner reads"
  on public.profiles for select
  to authenticated
  using ((select auth.uid()) = id);

create policy "profiles: owner updates"
  on public.profiles for update
  to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

-- Supabase grants every privilege on new public tables to anon and authenticated by default.
-- Row security still applies, but the grants are narrowed so the intent is explicit: anonymous
-- callers get nothing, signed-in users read and edit only their settings columns.
revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant update (display_name, time_zone, day_rollover_hour) on public.profiles to authenticated;

create function public.create_profile_for_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id);
  return new;
end;
$$;

revoke execute on function public.create_profile_for_new_user() from public, anon, authenticated;

create trigger on_auth_user_created_create_profile
  after insert on auth.users
  for each row execute function public.create_profile_for_new_user();

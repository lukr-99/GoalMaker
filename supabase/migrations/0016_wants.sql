-- 0016: wants and their cooldowns (spec: stories 104 to 108; docs/wants.md; M8-03).
--
-- A want is something the owner would like to buy, written down with the reason, which waits out a
-- cooldown before it is decided: bought or dropped, with the day and an optional note. The cooldown
-- is worked out once, when the want is added, from its price and the owner's thresholds
-- (contracts/vectors/wants.json), and kept on the row as days and the day it cools, so changing the
-- thresholds later never moves a want that is already cooling. Claude may record the last price it
-- found with where and when; GoalMaker itself never fetches from a shop.
--
-- The thresholds are one synced row per owner, want_cooldowns, so both apps and the connector give a
-- new want the same cooldown. Without the row, the defaults in the vectors apply. Its id is a UUID
-- version 5 of 'want-cooldowns/<owner>', so two devices that make it offline make the same row.

create table public.wants (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 200),
  reason text not null check (char_length(reason) between 1 and 2000),
  link text check (link is null or char_length(link) between 1 and 2000),
  price double precision check (price is null or (price >= 0 and price <= 100000000)),
  currency text not null default 'CZK' check (currency ~ '^[A-Z]{3}$'),
  area_id uuid,
  cooldown_days integer not null check (cooldown_days between 0 and 365),
  added_on date not null,
  cools_until date not null,
  decision text check (decision is null or decision in ('bought', 'dropped')),
  decided_at timestamptz,
  decision_note text not null default '' check (char_length(decision_note) <= 2000),
  checked_price double precision check (checked_price is null or (checked_price >= 0 and checked_price <= 100000000)),
  checked_at timestamptz,
  checked_note text not null default '' check (char_length(checked_note) <= 4000),
  made_by text not null check (made_by in ('owner', 'claude')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, owner_id),
  foreign key (area_id, owner_id) references public.areas (id, owner_id) on delete set null (area_id),
  constraint wants_cools_after_its_days check (cools_until = added_on + cooldown_days),
  constraint wants_decided_with_a_time check ((decision is null) = (decided_at is null)),
  constraint wants_checked_with_a_time check (checked_price is null or checked_at is not null)
);

comment on table public.wants is 'Something the owner would like to buy, with the reason, waiting out a cooldown before it is decided.';
comment on column public.wants.cooldown_days is 'The days it waits, from its price when added unless the owner picked them.';
comment on column public.wants.cools_until is 'The planning day it becomes ready: added_on plus cooldown_days.';
comment on column public.wants.decision is 'bought or dropped; null while it cools or waits to be decided. Reopening clears it.';
comment on column public.wants.checked_price is 'The last price Claude found, in the want''s currency, with checked_at and a note of where.';
comment on column public.wants.made_by is 'Who added the want, owner or claude; set once, like a task''s.';

create table public.want_cooldowns (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  small_under double precision not null default 1000 check (small_under >= 0),
  small_days integer not null default 7 check (small_days between 0 and 365),
  medium_under double precision not null default 10000,
  medium_days integer not null default 30 check (medium_days between 0 and 365),
  large_days integer not null default 90 check (large_days between 0 and 365),
  unpriced_days integer not null default 30 check (unpriced_days between 0 and 365),
  currency text not null default 'CZK' check (currency ~ '^[A-Z]{3}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (owner_id),
  constraint want_cooldowns_thresholds_in_order check (medium_under >= small_under)
);

comment on table public.want_cooldowns is 'The owner''s cooldown thresholds for new wants, one row; the defaults apply without it.';

-- Who added a want is history, like a task's maker: the same rule, the same function (0015).
create trigger wants_made_by before insert or update on public.wants
  for each row execute function public.stamp_task_maker();

-- The same wiring as every synced table.
do $$
declare
  synced text;
begin
  foreach synced in array array['wants', 'want_cooldowns'] loop
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

create index wants_state_idx on public.wants (owner_id, decision, cools_until);
create index wants_area_idx on public.wants (area_id);

-- The nightly purge (0005, 0009, 0010, 0013) also clears old want tombstones.
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

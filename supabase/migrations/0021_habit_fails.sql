-- 0021: the owner can say a habit failed (docs/habits.md, the owner's board item "add option to fail in
-- habits").
--
-- A skip excuses a period; a fail says it won't happen. A failed check-in misses its period at once,
-- today included, so it ends the streak and stops asking for the day. A failed check-in holds no value
-- and no skip: checking in or skipping takes the fail back. The trigger keeps that true for an app from
-- before 0021, which leaves the column out of its rows, so its check-in on a failed day still counts.

alter table public.habit_checkins
  add column failed boolean not null default false;

comment on column public.habit_checkins.failed is
  'The owner said the period failed: missed at once, today included. Cleared by a value or a skip.';

create or replace function public.clear_failed_checkin()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.value > 0 or new.skipped then
    new.failed := false;
  end if;
  return new;
end;
$$;

create trigger habit_checkins_clear_failed before insert or update on public.habit_checkins
  for each row execute function public.clear_failed_checkin();

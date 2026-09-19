-- After 0010: a habit serves the existing goal with check-ins and a pause; purging the goal lets the
-- habit go, and the purge knows habits, check-ins and pauses.
do $$
declare
  removed integer;
begin
  insert into public.habits (id, owner_id, name, cadence, weekdays, measure, target, unit, goal_id, starts_on)
  values ('bbbbbbbb-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Run', 'weekdays', 21,
          'amount', 5, 'km', 'eeeeeeee-0000-0000-0000-000000000001', '2026-09-01');
  insert into public.habit_checkins (id, owner_id, habit_id, day, value)
  values ('cccccccc-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-0000-0000-0000-000000000001', '2026-09-18', 6.5);
  insert into public.habit_pauses (id, owner_id, habit_id, starts_on, ends_on)
  values ('dddddddd-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-0000-0000-0000-000000000001', '2026-09-20', '2026-09-27');

  update public.goals set deleted_at = now() - interval '100 days';
  removed := public.purge_tombstones();
  if removed < 1 or exists (select 1 from public.goals) then
    raise exception 'the purge must clear the old goal tombstone';
  end if;
  if (select goal_id from public.habits where id = 'bbbbbbbb-0000-0000-0000-000000000001') is not null then
    raise exception 'a purged goal lets its habit go';
  end if;

  update public.habit_checkins set deleted_at = now() - interval '100 days';
  update public.habit_pauses set deleted_at = now() - interval '100 days';
  update public.habits set deleted_at = now() - interval '100 days';
  removed := public.purge_tombstones();
  if removed < 3 or exists (select 1 from public.habits) or exists (select 1 from public.habit_checkins)
     or exists (select 1 from public.habit_pauses) then
    raise exception 'the purge must clear old habit, check-in and pause tombstones';
  end if;
end;
$$;

-- After 0009: the existing task serves no goal and can be linked to one; deleting the goal lets the
-- task go; the purge knows goals and their entries.
do $$
declare
  removed integer;
begin
  if (select goal_id from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001') is not null then
    raise exception 'existing tasks serve no goal';
  end if;

  insert into public.goals (id, owner_id, title, horizon, period_start, progress_mode, target, unit)
  values ('eeeeeeee-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Run 80 km', 'month', '2026-09-01', 'number', 80, 'km');
  insert into public.goal_entries (id, owner_id, goal_id, day, amount)
  values ('ffffffff-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'eeeeeeee-0000-0000-0000-000000000001', '2026-09-18', 5);
  update public.tasks set goal_id = 'eeeeeeee-0000-0000-0000-000000000001' where id = 'aaaaaaaa-0000-0000-0000-000000000001';

  update public.goal_entries set deleted_at = now() - interval '100 days';
  update public.goals set deleted_at = now() - interval '100 days';
  removed := public.purge_tombstones();
  if removed < 2 or exists (select 1 from public.goals) or exists (select 1 from public.goal_entries) then
    raise exception 'the purge must clear old goal and entry tombstones';
  end if;
  if (select goal_id from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001') is not null then
    raise exception 'a purged goal lets its task go';
  end if;
end;
$$;

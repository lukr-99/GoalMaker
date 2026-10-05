-- After 0024: the habits from before are untouched, a weekly and a monthly habit can be a limit, a
-- limit can be 0, and a habit to build still needs at least one.
do $$
begin
  if (select count(*) from public.habits where id in ('dddddddd-2400-0000-0000-000000000001',
      'dddddddd-2400-0000-0000-000000000002')) <> 2 then
    raise exception 'the habits from before are still there';
  end if;

  insert into public.habits (id, owner_id, name, cadence, times, measure, direction, starts_on)
  values ('dddddddd-2400-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Takeaway',
          'per_week', 2, 'check', 'at_most', '2026-10-05');
  insert into public.habits (id, owner_id, name, cadence, times, measure, target, unit, direction, starts_on)
  values ('dddddddd-2400-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Drinks',
          'per_month', 1, 'count', 5, null, 'at_most', '2026-10-05');
  insert into public.habits (id, owner_id, name, cadence, times, measure, direction, starts_on)
  values ('dddddddd-2400-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Casino',
          'per_week', 0, 'check', 'at_most', '2026-10-05');
  insert into public.habits (id, owner_id, name, cadence, measure, target, direction, starts_on)
  values ('dddddddd-2400-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Cigarettes',
          'daily', 'count', 0, 'at_most', '2026-10-05');

  begin
    insert into public.habits (id, owner_id, name, cadence, times, measure, starts_on)
    values ('dddddddd-2400-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'Nothing',
            'per_week', 0, 'check', '2026-10-05');
    raise exception 'a habit to build with 0 times a week was let in';
  exception when check_violation then
    null;
  end;
end;
$$;

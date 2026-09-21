-- After 0014: the habit from before still counts up, a daily habit can be a limit instead, and a
-- weekly or monthly one cannot.
do $$
begin
  if (select direction from public.habits where id = 'dddddddd-1111-0000-0000-000000000001') <> 'at_least' then
    raise exception 'a habit from before 0014 is one to build';
  end if;

  insert into public.habits (id, owner_id, name, cadence, measure, target, unit, starts_on, direction)
  values ('dddddddd-1111-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Snacks',
          'daily', 'count', 2, 'snacks', '2026-09-01', 'at_most');
  insert into public.habits (id, owner_id, name, cadence, measure, starts_on, direction)
  values ('dddddddd-1111-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Smoking',
          'daily', 'check', '2026-09-01', 'at_most');

  begin
    insert into public.habits (id, owner_id, name, cadence, times, measure, target, starts_on, direction)
    values ('dddddddd-1111-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Takeaway',
            'per_week', 2, 'count', 1, '2026-09-01', 'at_most');
    raise exception 'a weekly habit must not be a limit';
  exception when check_violation then null;
  end;

  begin
    insert into public.habits (id, owner_id, name, cadence, measure, starts_on, direction)
    values ('dddddddd-1111-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Odd',
            'daily', 'check', '2026-09-01', 'sideways');
    raise exception 'a habit is at_least or at_most';
  exception when check_violation then null;
  end;
end;
$$;

-- After 0021: the check-in from before is not failed, a day can fail, and a check-in that leaves the
-- column out (what an app from before 0021 sends) takes the fail back once it holds a value.
do $$
begin
  if (select failed from public.habit_checkins where id = 'eeeeeeee-2100-0000-0000-000000000001') then
    raise exception 'a check-in from before 0021 is not failed';
  end if;

  insert into public.habit_checkins (id, owner_id, habit_id, day, value, failed)
  values ('eeeeeeee-2100-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
          'dddddddd-2100-0000-0000-000000000001', '2026-10-02', 0, true);
  if not (select failed from public.habit_checkins where id = 'eeeeeeee-2100-0000-0000-000000000002') then
    raise exception 'a day can fail';
  end if;

  update public.habit_checkins set value = 1 where id = 'eeeeeeee-2100-0000-0000-000000000002';
  if (select failed from public.habit_checkins where id = 'eeeeeeee-2100-0000-0000-000000000002') then
    raise exception 'checking in takes the fail back';
  end if;
end;
$$;

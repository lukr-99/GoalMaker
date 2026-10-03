-- After 0022: the habit from before doesn't remind, it can be given a time, and an edit that leaves the
-- column out (what an app from before 0022 sends) keeps the time.
do $$
begin
  if (select remind_at from public.habits where id = 'dddddddd-2200-0000-0000-000000000001') is not null then
    raise exception 'a habit from before 0022 does not remind';
  end if;

  update public.habits set remind_at = '20:30' where id = 'dddddddd-2200-0000-0000-000000000001';
  update public.habits set name = 'Read, renamed' where id = 'dddddddd-2200-0000-0000-000000000001';
  if (select remind_at from public.habits where id = 'dddddddd-2200-0000-0000-000000000001') <> '20:30'::time then
    raise exception 'an edit that leaves the column out keeps the reminder';
  end if;
end;
$$;

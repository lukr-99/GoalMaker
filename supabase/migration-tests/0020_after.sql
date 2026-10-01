-- After 0020: the habit from before still shows on Today, it can be kept off, and an edit that leaves
-- the column out (what an app from before 0020 sends) keeps what was set.
do $$
begin
  if not (select show_on_today from public.habits where id = 'dddddddd-2000-0000-0000-000000000001') then
    raise exception 'a habit from before 0020 shows on Today';
  end if;

  update public.habits set show_on_today = false where id = 'dddddddd-2000-0000-0000-000000000001';
  update public.habits set name = 'Water, renamed' where id = 'dddddddd-2000-0000-0000-000000000001';
  if (select show_on_today from public.habits where id = 'dddddddd-2000-0000-0000-000000000001') then
    raise exception 'an edit that leaves the column out keeps the habit off Today';
  end if;

  begin
    update public.habits set show_on_today = null where id = 'dddddddd-2000-0000-0000-000000000001';
    raise exception 'show_on_today is never null';
  exception when not_null_violation then null;
  end;
end;
$$;

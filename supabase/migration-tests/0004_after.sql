-- After 0004: existing rows are untouched, the log starts empty, and new changes are logged.
do $$
begin
  if (select count(*) from public.activity_log) <> 0 then
    raise exception 'the migration must not invent history';
  end if;
  if not exists (select 1 from public.tasks where title = 'Run' and area_id is not null) then
    raise exception 'existing task changed';
  end if;
  update public.tasks set title = 'Run 5 km' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
  if (select count(*) from public.activity_log where action = 'update' and entity = 'tasks') <> 1 then
    raise exception 'an update after 0004 was not logged';
  end if;
end;
$$;

-- After 0026: what was there is untouched, an event can be added in the area, Claude's event says so,
-- the checks hold and adding events is in the activity log.
do $$
begin
  if (select count(*) from public.tasks where id = 'aaaaaaaa-2600-0000-0000-000000000002') <> 1 then
    raise exception 'the task from before is still there';
  end if;
  if exists (select 1 from public.events) then
    raise exception 'there are no events until the owner adds one';
  end if;

  insert into public.events (id, owner_id, title, starts_on, ends_on, area_id)
  values ('aaaaaaaa-2600-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Trip to Rome',
          '2026-10-30', '2026-11-02', 'aaaaaaaa-2600-0000-0000-000000000001');
  if (select made_by from public.events where id = 'aaaaaaaa-2600-0000-0000-000000000003') <> 'owner' then
    raise exception 'an event the owner adds is the owner''s';
  end if;

  perform set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
  insert into public.events (id, owner_id, title, starts_on, ends_on)
  values ('aaaaaaaa-2600-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Conference',
          '2026-11-10', '2026-11-10');
  perform set_config('request.headers', '{}', true);
  if (select made_by from public.events where id = 'aaaaaaaa-2600-0000-0000-000000000004') <> 'claude' then
    raise exception 'an event Claude adds is Claude''s';
  end if;

  begin
    insert into public.events (id, owner_id, title, starts_on, ends_on)
    values ('aaaaaaaa-2600-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Backwards',
            '2026-11-10', '2026-11-09');
    raise exception 'an event that ends before it starts was let in';
  exception when check_violation then
    null;
  end;

  if (select count(*) from public.activity_log where entity = 'events') <> 2 then
    raise exception 'adding events is in the activity log';
  end if;
end;
$$;

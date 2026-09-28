-- After 0016: what was there is untouched, a want can be added in the area, and Claude's want says so.
do $$
begin
  if (select count(*) from public.tasks where id = 'aaaaaaaa-1600-0000-0000-000000000002') <> 1 then
    raise exception 'the tasks from before are still there';
  end if;
  if exists (select 1 from public.wants) or exists (select 1 from public.want_cooldowns) then
    raise exception 'nothing is wanted until the owner says so';
  end if;

  insert into public.wants (id, owner_id, title, reason, price, area_id, cooldown_days, added_on, cools_until)
  values ('aaaaaaaa-1600-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Trail shoes',
          'The old ones have holes', 3400, 'aaaaaaaa-1600-0000-0000-000000000001', 30, '2026-09-28', '2026-10-28');
  if (select made_by from public.wants where id = 'aaaaaaaa-1600-0000-0000-000000000003') <> 'owner' then
    raise exception 'a want the owner adds is the owner''s';
  end if;

  perform set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
  insert into public.wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until)
  values ('aaaaaaaa-1600-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Kindle',
          'Reading at night', 30, '2026-09-28', '2026-10-28');
  perform set_config('request.headers', '{}', true);
  if (select made_by from public.wants where id = 'aaaaaaaa-1600-0000-0000-000000000004') <> 'claude' then
    raise exception 'a want Claude adds is Claude''s';
  end if;
  if (select count(*) from public.activity_log where entity = 'wants') <> 2 then
    raise exception 'adding a want is in the activity log';
  end if;
end;
$$;

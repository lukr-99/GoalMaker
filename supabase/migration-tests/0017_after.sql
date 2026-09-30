-- After 0017: what was there is untouched, Tally starts empty, a PC's day can count toward a
-- project, and only the owner's own words reach the activity log.
do $$
begin
  if (select count(*) from public.wants where id = 'aaaaaaaa-1700-0000-0000-000000000002') <> 1 then
    raise exception 'the wants from before are still there';
  end if;
  if exists (select 1 from public.tally_days) or exists (select 1 from public.tally_rules)
     or exists (select 1 from public.tally_categories) then
    raise exception 'Tally is off until the owner turns it on';
  end if;

  insert into public.tally_days (id, owner_id, day, device, device_kind, category, project_id, minutes)
  values ('aaaaaaaa-1700-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '2026-09-28',
          'd1e57000-0000-4000-8000-00000000aaaa', 'pc', 'coding', 'aaaaaaaa-1700-0000-0000-000000000001', 95);
  insert into public.tally_rules (id, owner_id, match, pattern, platform, category)
  values ('aaaaaaaa-1700-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'title',
          'Chess lesson', 'windows', 'study');

  if (select count(*) from public.activity_log where entity = 'tally_rules') <> 1 then
    raise exception 'a rule the owner writes is in the activity log';
  end if;
  if exists (select 1 from public.activity_log where entity = 'tally_days') then
    raise exception 'a device rewriting its minutes is not something to undo';
  end if;
end;
$$;

-- After 0025: the want from before is a want, a need can be added without a reason or a cooldown, and
-- a want still needs both.
do $$
begin
  if (select kind from public.wants where id = 'aaaaaaaa-2500-0000-0000-000000000001') <> 'want' then
    raise exception 'a want from before is a want';
  end if;

  insert into public.wants (id, owner_id, title, reason, kind, need_by, cooldown_days, added_on, cools_until, made_by)
  values ('aaaaaaaa-2500-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Winter tyres',
          '', 'need', '2026-11-01', 0, '2026-10-05', '2026-10-05', 'owner');

  begin
    insert into public.wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until, made_by)
    values ('aaaaaaaa-2500-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Reasonless',
            '', 7, '2026-10-05', '2026-10-12', 'owner');
    raise exception 'a want without a reason was let in';
  exception when check_violation then
    null;
  end;
end;
$$;

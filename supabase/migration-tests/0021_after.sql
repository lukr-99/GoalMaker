-- After 0021: what was there is untouched, a life goal and its picture can be added in the area,
-- Claude's life goal says so, and the pictures bucket is there and private.
do $$
begin
  if (select count(*) from public.wants where id = 'aaaaaaaa-2100-0000-0000-000000000002') <> 1 then
    raise exception 'the wants from before are still there';
  end if;
  if exists (select 1 from public.life_goals) or exists (select 1 from public.life_goal_pictures) then
    raise exception 'there are no life goals until the owner writes one';
  end if;
  if (select public from storage.buckets where id = 'life-goal-pictures') is distinct from false then
    raise exception 'the pictures bucket is there and private';
  end if;

  insert into public.life_goals (id, owner_id, title, why, by_date, area_id)
  values ('aaaaaaaa-2100-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Own an Audi R8',
          'Proof that the work paid off', '2036-10-04', 'aaaaaaaa-2100-0000-0000-000000000001');
  if (select made_by || ' ' || status from public.life_goals where id = 'aaaaaaaa-2100-0000-0000-000000000003')
     <> 'owner open' then
    raise exception 'a life goal the owner adds is the owner''s, and open';
  end if;
  insert into public.life_goal_pictures (id, owner_id, life_goal_id, width, height)
  values ('aaaaaaaa-2100-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
          'aaaaaaaa-2100-0000-0000-000000000003', 1600, 900);

  perform set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
  insert into public.life_goals (id, owner_id, title, why)
  values ('aaaaaaaa-2100-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Run a marathon',
          'To know I can');
  perform set_config('request.headers', '{}', true);
  if (select made_by from public.life_goals where id = 'aaaaaaaa-2100-0000-0000-000000000005') <> 'claude' then
    raise exception 'a life goal Claude adds is Claude''s';
  end if;
  if (select count(*) from public.activity_log where entity in ('life_goals', 'life_goal_pictures')) <> 3 then
    raise exception 'adding life goals and pictures is in the activity log';
  end if;
end;
$$;

-- After 0030: the tag from before is for everything, and a tag can be given to the area or the
-- project, but not to both.
do $$
begin
  if (select count(*) from public.tags where id = 'cccccccc-3000-0000-0000-000000000001'
        and area_id is null and project_id is null) <> 1 then
    raise exception 'a tag that was there before is for everything';
  end if;

  update public.tags set area_id = 'aaaaaaaa-3000-0000-0000-000000000001'
  where id = 'cccccccc-3000-0000-0000-000000000001';
  update public.tags set area_id = null, project_id = 'bbbbbbbb-3000-0000-0000-000000000001'
  where id = 'cccccccc-3000-0000-0000-000000000001';
  if (select project_id from public.tags where id = 'cccccccc-3000-0000-0000-000000000001')
      <> 'bbbbbbbb-3000-0000-0000-000000000001' then
    raise exception 'a tag can belong to a project';
  end if;

  begin
    update public.tags set area_id = 'aaaaaaaa-3000-0000-0000-000000000001'
    where id = 'cccccccc-3000-0000-0000-000000000001';
    raise exception 'a tag belonged to an area and a project at once';
  exception when check_violation then
    null;
  end;
end;
$$;

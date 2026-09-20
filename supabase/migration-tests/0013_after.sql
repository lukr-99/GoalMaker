-- After 0013: the task from before is a plain task with no project, and it can join one. A purged
-- project lets its items go without taking them with it, and the purge knows projects and milestones.
do $$
declare
  removed integer;
begin
  if (select project_id from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') is not null
     or (select item_type from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') <> 'task'
     or (select priority from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') <> 'normal' then
    raise exception 'a task from before 0013 is a plain task at normal priority';
  end if;

  insert into public.projects (id, owner_id, name, area_id, repository_url)
  values ('aaaaaaaa-2222-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker',
          'aaaaaaaa-1111-0000-0000-000000000001', 'https://github.com/owner/goalmaker');
  insert into public.project_milestones (id, owner_id, project_id, name)
  values ('bbbbbbbb-2222-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
          'aaaaaaaa-2222-0000-0000-000000000001', 'M5');
  update public.tasks
  set project_id = 'aaaaaaaa-2222-0000-0000-000000000001',
      board_column = 'doing',
      priority = 'high',
      milestone_id = 'bbbbbbbb-2222-0000-0000-000000000001'
  where id = 'cccccccc-1111-0000-0000-000000000001';

  update public.projects set deleted_at = now() - interval '100 days';
  update public.project_milestones set deleted_at = now() - interval '100 days';
  removed := public.purge_tombstones();
  if removed < 2 or exists (select 1 from public.projects) or exists (select 1 from public.project_milestones) then
    raise exception 'the purge must clear old project and milestone tombstones';
  end if;
  if not exists (select 1 from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') then
    raise exception 'a purged project must not take its items with it';
  end if;
  if (select project_id from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') is not null
     or (select board_column from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') is not null
     or (select milestone_id from public.tasks where id = 'cccccccc-1111-0000-0000-000000000001') is not null then
    raise exception 'an item of a purged project keeps neither its project, its column nor its milestone';
  end if;
end;
$$;

-- After 0015: the tasks from before say who made them, filling that in logged nothing new, and a new
-- task takes the actor when it doesn't say.
do $$
begin
  if (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000001') <> 'owner' then
    raise exception 'a task the owner made before 0015 is the owner''s';
  end if;
  if (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000002') <> 'claude' then
    raise exception 'a task Claude made before 0015 is Claude''s, from the activity log';
  end if;
  if (select count(*) from public.activity_log where entity = 'tasks') <> 2 then
    raise exception 'filling in who made a task is not a change of its own';
  end if;

  perform set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
  insert into public.tasks (id, owner_id, title)
  values ('aaaaaaaa-1500-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'New from Claude');
  perform set_config('request.headers', '{}', true);
  if (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000003') <> 'claude' then
    raise exception 'a new task that doesn''t say takes the actor';
  end if;
end;
$$;

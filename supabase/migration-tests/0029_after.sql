-- After 0029: the task from before is untouched and is not on the Focus Board, and a task can be put
-- on the board with a position and taken off again.
do $$
begin
  if (select count(*) from public.tasks where id = 'aaaaaaaa-2900-0000-0000-000000000001') <> 1 then
    raise exception 'the task from before is still there';
  end if;
  if (select focus_position from public.tasks where id = 'aaaaaaaa-2900-0000-0000-000000000001') is not null then
    raise exception 'a task that was there before is not on the Focus Board';
  end if;

  update public.tasks set focus_position = 1.5 where id = 'aaaaaaaa-2900-0000-0000-000000000001';
  if (select focus_position from public.tasks where id = 'aaaaaaaa-2900-0000-0000-000000000001') <> 1.5 then
    raise exception 'a task can be placed on the Focus Board with a fractional position';
  end if;
  update public.tasks set focus_position = null where id = 'aaaaaaaa-2900-0000-0000-000000000001';
  if (select focus_position from public.tasks where id = 'aaaaaaaa-2900-0000-0000-000000000001') is not null then
    raise exception 'a task can be taken off the Focus Board';
  end if;

  if not exists (select 1 from pg_indexes where schemaname = 'public' and indexname = 'tasks_focus_position_idx') then
    raise exception 'the Focus Board order has an index';
  end if;
end;
$$;

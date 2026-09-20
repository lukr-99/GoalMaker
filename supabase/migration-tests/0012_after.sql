-- After 0012: the task from before has been moved no times, counts up as it slides from day to day,
-- and refuses a count below zero.
do $$
begin
  if (select moved_count from public.tasks where id = 'cccccccc-0012-0000-0000-000000000001') <> 0 then
    raise exception 'a task from before 0012 starts at no moves';
  end if;

  update public.tasks set planned_date = '2026-09-19', moved_count = moved_count + 1
  where id = 'cccccccc-0012-0000-0000-000000000001';
  if (select moved_count from public.tasks where id = 'cccccccc-0012-0000-0000-000000000001') <> 1 then
    raise exception 'moving a planned task to another day counts';
  end if;

  begin
    update public.tasks set moved_count = -1 where id = 'cccccccc-0012-0000-0000-000000000001';
    raise exception 'a count below zero must be refused';
  exception
    when check_violation then null;
  end;
end;
$$;

-- After 0003: the six synced tables exist, are in the Realtime publication, and accept a task.
do $$
declare
  missing text;
begin
  select string_agg(name, ', ') into missing
  from unnest(array['areas', 'tags', 'tasks', 'task_steps', 'task_tags', 'reminders']) as name
  where not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = name);
  if missing is not null then
    raise exception 'not in the realtime publication: %', missing;
  end if;

  insert into public.tasks (id, owner_id, title)
  values ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'First');
  if not exists (select 1 from public.profiles where id = '11111111-1111-1111-1111-111111111111') then
    raise exception 'existing profile lost';
  end if;
end;
$$;

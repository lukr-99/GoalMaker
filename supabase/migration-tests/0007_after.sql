-- After 0007: the old log entries stand and can be undone, the system actor is allowed, and the user
-- can make a connector link that resolves to them.
do $$
declare
  secret text;
  resolved uuid;
begin
  if exists (select 1 from public.activity_log where undone_at is not null) then
    raise exception 'existing log entries must stand';
  end if;

  perform set_config('request.jwt.claims',
    '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
  perform public.undo_activity(
    (select max(id) from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001'));
  if (select title from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001') <> 'Run' then
    raise exception 'an entry from before 0007 must be undoable';
  end if;

  insert into public.activity_log (owner_id, entity, entity_id, action, actor, after)
  values ('11111111-1111-1111-1111-111111111111', 'tasks', 'aaaaaaaa-0000-0000-0000-000000000001',
          'update', 'system', '{}');

  secret := public.create_connector_link();
  select owner_id into resolved from public.connector_resolve(encode(extensions.digest(secret, 'sha256'), 'hex'));
  if resolved is distinct from '11111111-1111-1111-1111-111111111111'::uuid then
    raise exception 'a new link must resolve to its owner';
  end if;
end;
$$;

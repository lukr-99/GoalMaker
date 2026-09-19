-- After 0006: existing areas are in use, can be archived and brought back, and ritual runs are
-- recorded once per ritual and day; the purge knows the new table.
do $$
declare
  removed integer;
begin
  if exists (select 1 from public.areas where archived_at is not null) then
    raise exception 'existing areas must stay in use';
  end if;
  if not exists (select 1 from public.tasks where title = 'Run' and area_id = 'bbbbbbbb-0000-0000-0000-000000000001') then
    raise exception 'existing task changed';
  end if;

  update public.areas set archived_at = now() where id = 'bbbbbbbb-0000-0000-0000-000000000002';
  update public.areas set archived_at = null where id = 'bbbbbbbb-0000-0000-0000-000000000002';

  insert into public.ritual_runs (id, owner_id, ritual, day)
  values ('cccccccc-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'plan_tomorrow', '2026-09-18');
  begin
    insert into public.ritual_runs (id, owner_id, ritual, day)
    values ('cccccccc-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'plan_tomorrow', '2026-09-18');
    raise exception 'a second run for the same ritual and day was accepted';
  exception when unique_violation then
    null;
  end;

  update public.ritual_runs set deleted_at = now() - interval '100 days' where id = 'cccccccc-0000-0000-0000-000000000001';
  -- Its own statement: SQL doesn't promise to evaluate an OR from left to right.
  removed := public.purge_tombstones();
  if removed < 1 or exists (select 1 from public.ritual_runs) then
    raise exception 'the purge must clear old ritual tombstones';
  end if;
end;
$$;

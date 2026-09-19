-- After 0008: the ritual run is untouched, a review can be saved and undone, and the purge knows reviews.
do $$
declare
  removed integer;
begin
  if not exists (select 1 from public.ritual_runs where id = 'cccccccc-0000-0000-0000-000000000001') then
    raise exception 'existing ritual runs must stay';
  end if;

  perform set_config('request.jwt.claims',
    '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
  insert into public.reviews (id, owner_id, kind, period_start, summary)
  values ('dddddddd-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'weekly', '2026-09-14', 'Wins');
  perform public.undo_activity(
    (select max(id) from public.activity_log where entity_id = 'dddddddd-0000-0000-0000-000000000001'));
  if (select deleted_at from public.reviews where id = 'dddddddd-0000-0000-0000-000000000001') is null then
    raise exception 'undo must cover reviews';
  end if;

  update public.reviews set deleted_at = now() - interval '100 days' where id = 'dddddddd-0000-0000-0000-000000000001';
  -- Its own statement: SQL doesn't promise to evaluate an OR from left to right.
  removed := public.purge_tombstones();
  if removed < 1 or exists (select 1 from public.reviews) then
    raise exception 'the purge must clear old review tombstones';
  end if;
end;
$$;

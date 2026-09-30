-- After 0018: an existing project keeps done items 14 days, nothing is archived yet, a done item can
-- be archived by hand, and reopening it (even without naming the column) brings it back.
do $$
begin
  if (select archive_after_days from public.projects where id = 'aaaaaaaa-1800-0000-0000-000000000001') <> 14 then
    raise exception 'a project from before keeps done items for 14 days';
  end if;
  if exists (select 1 from public.tasks where board_archived_at is not null) then
    raise exception 'nothing leaves the board by itself when the migration runs';
  end if;

  update public.tasks set board_archived_at = now() where id = 'aaaaaaaa-1800-0000-0000-000000000002';
  if (select board_archived_at from public.tasks where id = 'aaaaaaaa-1800-0000-0000-000000000002') is null then
    raise exception 'a done item can be archived by hand';
  end if;

  -- What an app from before 0018 sends when it reopens the item: the status, not the new column.
  update public.tasks set status = 'open', completed_at = null, board_column = 'todo'
  where id = 'aaaaaaaa-1800-0000-0000-000000000002';
  if (select board_archived_at from public.tasks where id = 'aaaaaaaa-1800-0000-0000-000000000002') is not null then
    raise exception 'reopening an archived item brings it back to the board';
  end if;

  update public.tasks set board_archived_at = now() where id = 'aaaaaaaa-1800-0000-0000-000000000003';
  if (select board_archived_at from public.tasks where id = 'aaaaaaaa-1800-0000-0000-000000000003') is not null then
    raise exception 'an open item never leaves the board';
  end if;
end;
$$;

-- 0018: done items leave the board (docs/projects.md, the owner's board items of 2026-09-30).
--
-- A project's Done column grew without end. Now a done item leaves the board a number of days after
-- the planning day it was finished: the project's archive_after_days, 14 unless the owner changed it,
-- or never when it is null. Any done item can also be archived by hand (board_archived_at). Leaving
-- the board is only about the board: the task stays done, in the archive and in search, and
-- reopening it clears board_archived_at so it comes back. Which items show is pinned by the 'archive'
-- group of contracts/vectors/projects.json.

alter table public.projects
  add column archive_after_days integer default 14 check (archive_after_days between 1 and 365);

comment on column public.projects.archive_after_days is
  'Days a done item stays on the board after the planning day it was finished; null keeps it until archived by hand.';

alter table public.tasks
  add column board_archived_at timestamptz;

comment on column public.tasks.board_archived_at is
  'When the owner took a done item off its project''s board by hand; cleared when it is reopened.';

-- Only a done project item stays archived: reopening it, dropping it or taking it out of its project
-- brings it back. A trigger rather than a check, so an app from before 0018, which doesn't know the
-- column, can still reopen an archived item without its row being refused.
create function public.clear_board_archive()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.status <> 'done' or new.project_id is null then
    new.board_archived_at := null;
  end if;
  return new;
end;
$$;

revoke execute on function public.clear_board_archive() from public, anon, authenticated;

create trigger tasks_clear_board_archive before insert or update on public.tasks
  for each row execute function public.clear_board_archive();

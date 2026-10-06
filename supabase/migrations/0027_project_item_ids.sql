-- 0027: short ids for project items (docs/projects.md, "Item ids"; the owner's board item "Start
-- giving items in Projects ID").
--
-- A project may have a key like GM, one per project among the owner's live projects, and each of its
-- items a number, so an item reads GM-12 (#12 in a project without a key). The rules for keys and for
-- reading an id are pinned by the 'itemKeys' group of contracts/vectors/projects.json.
--
-- The server gives the numbers, so two devices adding offline never pick the same one. A trigger owns
-- the column: an item added to a project, or moved to another one, takes the next number after the
-- project's highest, deleted items counted so a number is not reused; an item that leaves every
-- project loses it; any other change keeps the number it has, whatever the client sent. Clients push
-- whole rows and an app from before this column sends none, so what they send is never trusted.
--
-- Undo (0009) puts a row back through an update. It marks that with goalmaker.undoing, so an undone
-- move back into a project gets its old number again when nobody has taken it in the meantime.
--
-- The trigger is named so it runs before tasks_made_by and tasks_stamp (Postgres runs a table's
-- before triggers in name order), and like every other column change it is logged by tasks_log.

alter table public.projects add column item_key text check (item_key ~ '^[A-Z][A-Z0-9]{1,5}$');

comment on column public.projects.item_key is
  'The key its items'' ids start with, like GM for GM-12: 2 to 6 capital letters or digits, starting with a letter; one per live project of an owner, null for none.';

create unique index projects_item_key_idx on public.projects (owner_id, item_key)
  where deleted_at is null and item_key is not null;

alter table public.tasks add column item_number integer check (item_number > 0);

comment on column public.tasks.item_number is
  'The item''s number in its project, given by the server (trigger tasks_item_number); null outside a project.';

create unique index tasks_item_number_idx on public.tasks (project_id, item_number)
  where item_number is not null;

-- Every project item there is, numbered per project in the order it was made. The log trigger is off
-- for this, as in 0015: numbering is not a change the owner made. The stamp trigger moves updated_at,
-- which carries the numbers to the replicas.
alter table public.tasks disable trigger tasks_log;
update public.tasks t
set item_number = numbered.number
from (
  select id, row_number() over (partition by project_id order by created_at, id) as number
  from public.tasks
  where project_id is not null
) numbered
where t.id = numbered.id;
alter table public.tasks enable trigger tasks_log;

create function public.number_project_item()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  undoing boolean := coalesce(current_setting('goalmaker.undoing', true), '') = 'on';
  wanted integer := new.item_number;
begin
  if new.project_id is null then
    new.item_number := null;
    return new;
  end if;
  if tg_op = 'UPDATE' and old.project_id is not distinct from new.project_id then
    new.item_number := old.item_number;
    return new;
  end if;

  -- One numbering at a time per project: the next writer waits here and then sees this one's number.
  -- The function runs as the writer, so row security keeps it to the writer's own project and items
  -- (someone else's project is refused by the foreign key after this).
  perform 1 from public.projects where id = new.project_id for no key update;

  if undoing and wanted is not null and not exists (
    select 1 from public.tasks
    where project_id = new.project_id and item_number = wanted and id <> new.id
  ) then
    new.item_number := wanted;
  else
    new.item_number := coalesce(
      (select max(item_number) from public.tasks where project_id = new.project_id and id <> new.id), 0) + 1;
  end if;
  return new;
end;
$$;

comment on function public.number_project_item() is
  'Gives a project item the next number in its project when it is added or moved there, keeps it otherwise, and clears it outside a project.';

revoke execute on function public.number_project_item() from public, anon, authenticated;

create trigger tasks_item_number before insert or update on public.tasks
  for each row execute function public.number_project_item();

-- Undo as in 0009, with goalmaker.undoing set around the restoring update so the number trigger can
-- give an item back the number it had.
create or replace function public.undo_activity(entry_id bigint)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  entry public.activity_log;
  current_row jsonb;
  columns text;
begin
  select * into entry from public.activity_log where id = entry_id for update;
  if not found or entry.owner_id is distinct from auth.uid() then
    raise exception 'no such change' using errcode = 'P0002';
  end if;
  if entry.undone_at is not null then
    raise exception 'already undone' using errcode = '55000';
  end if;
  if not exists (
    select 1 from pg_catalog.pg_trigger t
    join pg_catalog.pg_class c on c.oid = t.tgrelid
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = entry.entity and t.tgname = entry.entity || '_log'
  ) then
    raise exception 'this change can''t be undone' using errcode = '22023';
  end if;

  execute format('select to_jsonb(t) from public.%I t where t.id = $1', entry.entity)
    into current_row using entry.entity_id;
  if current_row is null or (current_row - 'updated_at') <> (entry.after - 'updated_at') then
    raise exception 'changed since' using errcode = '40001';
  end if;

  if entry.action = 'create' then
    execute format('update public.%I set deleted_at = now() where id = $1', entry.entity)
      using entry.entity_id;
  else
    -- Every column the owner can change goes back to the entry's before snapshot.
    select string_agg(format('%I = r.%I', c.column_name, c.column_name), ', ')
    into columns
    from information_schema.columns c
    where c.table_schema = 'public' and c.table_name = entry.entity
      and c.column_name not in ('id', 'owner_id', 'created_at', 'updated_at');
    perform set_config('goalmaker.undoing', 'on', true);
    execute format(
      'update public.%I t set %s from jsonb_populate_record(null::public.%I, $1) r where t.id = $2',
      entry.entity, columns, entry.entity)
      using entry.before, entry.entity_id;
    perform set_config('goalmaker.undoing', '', true);
  end if;

  update public.activity_log set undone_at = now() where id = entry.id;
end;
$$;

revoke execute on function public.undo_activity(bigint) from public, anon;
grant execute on function public.undo_activity(bigint) to authenticated;

-- After 0027: every project item is numbered per project in the order it was made (deleted ones too,
-- the same moment by id), plain tasks have no number, projects no key, and the fill is not in the
-- activity log. A new item takes the next number.
do $$
declare
  numbers text;
begin
  select string_agg(title || '=' || coalesce(item_number::text, 'none'), ', ' order by title) into numbers
  from public.tasks;
  if numbers <> 'First=1, Plain=none, Relay a=1, Relay b=2, Second, deleted=2, Third=3' then
    raise exception 'the items are numbered per project in the order they were made, got %', numbers;
  end if;
  if exists (select 1 from public.projects where item_key is not null) then
    raise exception 'no project gets a key by itself';
  end if;
  if exists (select 1 from public.activity_log where action = 'update' and entity = 'tasks') then
    raise exception 'numbering the items is not a change of the owner''s';
  end if;

  insert into public.tasks (id, owner_id, title, project_id, board_column, item_number)
  values ('cccccccc-2700-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Fourth',
          'aaaaaaaa-2700-0000-0000-000000000001', 'todo', 1);
  if (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000004') <> 4 then
    raise exception 'a new item takes the next number after the deleted ones';
  end if;

  update public.projects set item_key = 'GM' where id = 'aaaaaaaa-2700-0000-0000-000000000001';
  begin
    update public.projects set item_key = 'GM' where id = 'aaaaaaaa-2700-0000-0000-000000000002';
    raise exception 'two of the owner''s projects got the same key';
  exception when unique_violation then
    null;
  end;
end;
$$;

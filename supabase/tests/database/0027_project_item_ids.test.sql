-- Project keys and item numbers (migration 0027, docs/projects.md "Item ids"). How the numbers of items
-- that were there before are filled in is checked by supabase/migration-tests/0027_after.sql.
begin;
create extension if not exists pgtap with schema extensions;
select plan(20);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's project, with the key the owner is about to use too.
insert into public.projects (id, owner_id, name, item_key)
values ('99999999-2700-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'GM');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.projects (id, name, item_key)
     values ('aaaaaaaa-2700-0000-0000-000000000001', 'GoalMaker', 'GM'),
            ('aaaaaaaa-2700-0000-0000-000000000002', 'Relay', null) $$,
  'another owner may use the same key, and a project may have none');
select throws_ok(
  $$ insert into public.projects (id, name, item_key) values ('aaaaaaaa-2700-0000-0000-000000000009', 'Again', 'GM') $$,
  '23505', null,
  'a key is used by one of the owner''s projects only');
select throws_ok(
  $$ update public.projects set item_key = 'gm' where id = 'aaaaaaaa-2700-0000-0000-000000000002' $$,
  '23514', null,
  'a key is kept in capitals');
select throws_ok(
  $$ update public.projects set item_key = 'G' where id = 'aaaaaaaa-2700-0000-0000-000000000002' $$,
  '23514', null,
  'a key has 2 to 6 characters');
select throws_ok(
  $$ update public.projects set item_key = '1GM' where id = 'aaaaaaaa-2700-0000-0000-000000000002' $$,
  '23514', null,
  'a key starts with a letter');

insert into public.tasks (id, title, project_id, board_column)
values ('cccccccc-2700-0000-0000-000000000001', 'First', 'aaaaaaaa-2700-0000-0000-000000000001', 'todo');
insert into public.tasks (id, title, project_id, board_column)
values ('cccccccc-2700-0000-0000-000000000002', 'Second', 'aaaaaaaa-2700-0000-0000-000000000001', 'todo');
insert into public.tasks (id, title, project_id, board_column, item_number)
values ('cccccccc-2700-0000-0000-000000000003', 'Third', 'aaaaaaaa-2700-0000-0000-000000000001', 'todo', 99);
insert into public.tasks (id, title) values ('cccccccc-2700-0000-0000-000000000004', 'Plain');
insert into public.tasks (id, title, item_number) values ('cccccccc-2700-0000-0000-000000000005', 'Plain too', 7);

select results_eq(
  $$ select item_number from public.tasks where project_id = 'aaaaaaaa-2700-0000-0000-000000000001' order by title $$,
  $$ values (1), (2), (3) $$,
  'items take 1, 2, 3 in the order they are added');
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000003'),
  3,
  'a number a client sends is not taken');
select is(
  (select count(*)::integer from public.tasks where project_id is null and item_number is not null),
  0,
  'a task outside a project has no number');

-- What a sync push of the whole row sends: an app that doesn't know the column sends none.
select lives_ok(
  $$ update public.tasks set title = 'Second, renamed', item_number = null
     where id = 'cccccccc-2700-0000-0000-000000000002' $$,
  'an edit sends no number');
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000002'),
  2,
  'and the item keeps its own');
update public.tasks set item_number = 1 where id = 'cccccccc-2700-0000-0000-000000000003';
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000003'),
  3,
  'nor can an edit pick another one');

select is(
  (select count(*)::integer from public.activity_log where entity_id = 'cccccccc-2700-0000-0000-000000000003'),
  1,
  'so a push with another number is no change and is not logged');

insert into public.tasks (id, title, project_id, board_column)
values ('cccccccc-2700-0000-0000-000000000006', 'Relay''s first', 'aaaaaaaa-2700-0000-0000-000000000002', 'todo');
update public.tasks set project_id = 'aaaaaaaa-2700-0000-0000-000000000002'
where id = 'cccccccc-2700-0000-0000-000000000002';
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000002'),
  2,
  'an item moved to another project takes the next number there');

-- Undo the move: the item goes back to GoalMaker with the number it had, which nobody took.
select undo_activity((
  select max(id) from public.activity_log where entity_id = 'cccccccc-2700-0000-0000-000000000002'));
select is(
  (select project_id::text || ' ' || item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000002'),
  'aaaaaaaa-2700-0000-0000-000000000001 2',
  'undoing a move gives the item back its old number');

update public.tasks set project_id = null, board_column = null where id = 'cccccccc-2700-0000-0000-000000000001';
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000001'),
  null,
  'an item that leaves its project loses its number');

update public.tasks set deleted_at = now() where id = 'cccccccc-2700-0000-0000-000000000003';
insert into public.tasks (id, title, project_id, board_column)
values ('cccccccc-2700-0000-0000-000000000007', 'Fourth', 'aaaaaaaa-2700-0000-0000-000000000001', 'todo');
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000007'),
  4,
  'a deleted item''s number is not given again');

update public.tasks set project_id = 'aaaaaaaa-2700-0000-0000-000000000001', board_column = 'todo'
where id = 'cccccccc-2700-0000-0000-000000000001';
select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000001'),
  5,
  'an item that comes back takes a new number');

-- A key freed by a deleted project can be used again.
update public.projects set deleted_at = now() where id = 'aaaaaaaa-2700-0000-0000-000000000001';
select lives_ok(
  $$ insert into public.projects (id, name, item_key) values ('aaaaaaaa-2700-0000-0000-000000000003', 'GoalMaker 2', 'GM') $$,
  'a deleted project''s key is free again');

select is(
  (select count(*)::integer from public.projects where item_key = 'GM'),
  2,
  'and the owner never sees the stranger''s key');

select is(
  (select item_number from public.tasks where id = 'cccccccc-2700-0000-0000-000000000005'),
  null,
  'a number sent with a plain task is dropped');

select * from finish();
rollback;

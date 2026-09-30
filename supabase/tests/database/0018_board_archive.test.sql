-- Done items leaving the board (migration 0018).
begin;
create extension if not exists pgtap with schema extensions;
select plan(8);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.projects (id, name) values ('aaaaaaaa-0000-0000-0000-000000000001', 'GoalMaker');
insert into public.tasks (id, title, status, completed_at, project_id, board_column)
values ('aaaaaaaa-0000-0000-0000-000000000002', 'Shipped', 'done', now(), 'aaaaaaaa-0000-0000-0000-000000000001', 'done');

select is(
  (select archive_after_days from public.projects where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  14,
  'a new project keeps done items for 14 days');
select lives_ok(
  $$ update public.projects set archive_after_days = null where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'a project can keep them until they are archived by hand');
select throws_ok(
  $$ update public.projects set archive_after_days = 0 where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '23514', null,
  'a number of days is at least one');
select lives_ok(
  $$ update public.tasks set board_archived_at = now() where id = 'aaaaaaaa-0000-0000-0000-000000000002' $$,
  'a done item is archived by hand');
select is(
  (select count(*)::integer from public.activity_log where entity = 'tasks' and entity_id = 'aaaaaaaa-0000-0000-0000-000000000002'
     and action = 'update' and after ? 'board_archived_at' and after ->> 'board_archived_at' is not null),
  1,
  'archiving is in the activity log, so it can be undone');
select lives_ok(
  $$ update public.tasks set status = 'open', completed_at = null, board_column = 'todo'
     where id = 'aaaaaaaa-0000-0000-0000-000000000002' $$,
  'the item is reopened without naming the new column');
select is(
  (select board_archived_at from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000002'),
  null,
  'and it is back on the board');
select is(
  (select title from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000002'),
  'Shipped',
  'leaving the board never touched the task itself');

select * from finish();
rollback;

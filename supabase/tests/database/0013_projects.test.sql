-- Row security and integrity for projects, their milestones and the board columns (migration 0013).
begin;
create extension if not exists pgtap with schema extensions;
select plan(16);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's project and milestone, made as the test runner.
insert into public.projects (id, owner_id, name)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs');
insert into public.project_milestones (id, owner_id, project_id, name)
values ('99999999-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
        '99999999-0000-0000-0000-000000000001', 'Their M1');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.projects (id, name) values ('aaaaaaaa-0000-0000-0000-000000000001', 'GoalMaker') $$,
  'a project needs only a name');
select is(
  (select owner_id from public.projects where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the project belongs to the signed-in user');
select lives_ok(
  $$ update public.projects
     set description = 'The planner', repository_url = 'https://github.com/owner/goalmaker',
         local_folder = 'F:\GoalMaker', status = 'paused'
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'a project carries its repository and its folder');
select throws_ok(
  $$ insert into public.projects (id, name, status) values ('aaaaaaaa-0000-0000-0000-000000000009', 'Odd', 'sleeping') $$,
  '23514', null,
  'a project is active, paused or done');

select lives_ok(
  $$ insert into public.project_milestones (id, project_id, name)
     values ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'M1') $$,
  'a milestone belongs to a project');
select throws_ok(
  $$ insert into public.project_milestones (id, project_id, name)
     values ('bbbbbbbb-0000-0000-0000-000000000009', '99999999-0000-0000-0000-000000000001', 'Sneaky') $$,
  '23503', null,
  'a milestone can''t join someone else''s project');

select lives_ok(
  $$ insert into public.tasks (id, title, project_id, item_type, board_column, priority, milestone_id)
     values ('cccccccc-0000-0000-0000-000000000001', 'Ship the board', 'aaaaaaaa-0000-0000-0000-000000000001',
             'task', 'todo', 'high', 'bbbbbbbb-0000-0000-0000-000000000001') $$,
  'a project item is a task with a column, a priority and a milestone');
select is(
  (select priority from public.tasks where id = 'cccccccc-0000-0000-0000-000000000001'),
  'high',
  'the priority is kept');
select lives_ok(
  $$ insert into public.tasks (id, title) values ('cccccccc-0000-0000-0000-000000000002', 'Buy milk') $$,
  'a task outside a project needs none of them');
select is(
  (select item_type || ' ' || priority from public.tasks where id = 'cccccccc-0000-0000-0000-000000000002'),
  'task normal',
  'a plain task is a task at normal priority');
select throws_ok(
  $$ insert into public.tasks (id, title, board_column) values ('cccccccc-0000-0000-0000-000000000009', 'Homeless', 'todo') $$,
  '23514', null,
  'a board column needs a project');
select throws_ok(
  $$ insert into public.tasks (id, title, project_id) values ('cccccccc-0000-0000-0000-000000000008', 'Column-less', 'aaaaaaaa-0000-0000-0000-000000000001') $$,
  '23514', null,
  'a project item sits in a column');
select throws_ok(
  $$ insert into public.tasks (id, title, project_id, board_column)
     values ('cccccccc-0000-0000-0000-000000000007', 'Sneaky', '99999999-0000-0000-0000-000000000001', 'todo') $$,
  '23503', null,
  'an item can''t join someone else''s project');
select throws_ok(
  $$ insert into public.tasks (id, title, project_id, board_column, milestone_id)
     values ('cccccccc-0000-0000-0000-000000000006', 'Wrong milestone', 'aaaaaaaa-0000-0000-0000-000000000001', 'todo',
             '99999999-0000-0000-0000-000000000002') $$,
  '23503', null,
  'a milestone has to belong to the item''s project');

select is(
  (select count(*) from public.projects),
  1::bigint,
  'the owner never sees the stranger''s project');

-- A project that goes lets its items stay, as tasks with no project.
update public.projects set deleted_at = now() - interval '100 days' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
set local role postgres;
select lives_ok($$ select public.purge_tombstones() $$, 'the purge clears an old project tombstone');

select * from finish();
rollback;

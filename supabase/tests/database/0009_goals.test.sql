-- Row security and integrity for goals, goal entries and tasks.goal_id (migration 0009).
begin;
create extension if not exists pgtap with schema extensions;
select plan(14);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's goal, made as the test runner.
insert into public.goals (id, owner_id, title, horizon, period_start)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'year', '2026-01-01');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.goals (id, title, horizon, period_start) values ('eeeeeeee-0000-0000-0000-000000000001', 'Run a half marathon', 'year', '2026-01-01') $$,
  'the owner sets a yearly goal');
select lives_ok(
  $$ insert into public.goals (id, title, horizon, period_start, parent_id, progress_mode, target, unit)
     values ('eeeeeeee-0000-0000-0000-000000000002', 'Run 80 km', 'month', '2026-09-01', 'eeeeeeee-0000-0000-0000-000000000001', 'number', 80, 'km') $$,
  'a numeric monthly goal serves the yearly one');
select is(
  (select owner_id from public.goals where id = 'eeeeeeee-0000-0000-0000-000000000002'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the goal belongs to the signed-in user');
select throws_ok(
  $$ insert into public.goals (id, title, horizon, period_start) values ('eeeeeeee-0000-0000-0000-000000000003', 'Bad week', 'week', '2026-09-16') $$,
  '23514', null,
  'a week starts on its Monday');
select throws_ok(
  $$ insert into public.goals (id, title, horizon, period_start, progress_mode) values ('eeeeeeee-0000-0000-0000-000000000004', 'No target', 'week', '2026-09-14', 'number') $$,
  '23514', null,
  'a numeric goal needs a target');
select throws_ok(
  $$ insert into public.goals (id, title, horizon, period_start, status) values ('eeeeeeee-0000-0000-0000-000000000005', 'Done without a time', 'day', '2026-09-18', 'done') $$,
  '23514', null,
  'a done goal says when');
select lives_ok(
  $$ insert into public.goal_entries (id, goal_id, day, amount) values ('ffffffff-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000002', '2026-09-18', 5) $$,
  'the owner logs 5 km');
select throws_ok(
  $$ insert into public.goal_entries (id, goal_id, day, amount) values ('ffffffff-0000-0000-0000-000000000002', '99999999-0000-0000-0000-000000000001', '2026-09-18', 5) $$,
  '23503', null,
  'an entry can''t land on someone else''s goal');
select throws_ok(
  $$ insert into public.goal_entries (id, goal_id, day, amount) values ('ffffffff-0000-0000-0000-000000000003', 'eeeeeeee-0000-0000-0000-000000000002', '2026-09-18', 0) $$,
  '23514', null,
  'an entry is never zero');
select lives_ok(
  $$ insert into public.tasks (id, title, goal_id) values ('aaaaaaaa-0000-0000-0000-000000000001', 'Long run', 'eeeeeeee-0000-0000-0000-000000000002') $$,
  'a task serves a goal');
select throws_ok(
  $$ insert into public.tasks (id, title, goal_id) values ('aaaaaaaa-0000-0000-0000-000000000002', 'Sneaky', '99999999-0000-0000-0000-000000000001') $$,
  '23503', null,
  'a task can''t serve someone else''s goal');
select is((select count(*)::int from public.goals), 2, 'the owner sees only their own goals');

select lives_ok(
  $$ select public.undo_activity((select max(id) from public.activity_log where entity_id = 'ffffffff-0000-0000-0000-000000000001')) $$,
  'logging an amount can be undone, found by the table''s log trigger');

reset role;
select isnt(
  (select deleted_at from public.goal_entries where id = 'ffffffff-0000-0000-0000-000000000001'),
  null,
  'the undone entry is deleted softly');

select * from finish();
rollback;

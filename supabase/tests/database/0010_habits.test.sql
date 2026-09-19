-- Row security and integrity for habits, their check-ins and pauses (migration 0010).
begin;
create extension if not exists pgtap with schema extensions;
select plan(18);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's goal and habit, made as the test runner.
insert into public.goals (id, owner_id, title, horizon, period_start)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'year', '2026-01-01');
insert into public.habits (id, owner_id, name, starts_on)
values ('99999999-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'Theirs', '2026-09-01');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.goals (id, title, horizon, period_start, progress_mode, target, unit)
     values ('eeeeeeee-0000-0000-0000-000000000001', 'Run 80 km', 'month', '2026-09-01', 'number', 80, 'km') $$,
  'the owner sets a numeric goal');
select lives_ok(
  $$ insert into public.habits (id, name, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000001', 'Stretch', '2026-09-01') $$,
  'a daily check habit needs only a name and a start');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, weekdays, measure, target, unit, goal_id, starts_on)
     values ('bbbbbbbb-0000-0000-0000-000000000002', 'Run', 'weekdays', 21, 'amount', 5, 'km', 'eeeeeeee-0000-0000-0000-000000000001', '2026-09-01') $$,
  'a Monday, Wednesday and Friday run serves the km goal');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, times, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000003', 'Gym', 'per_week', 3, '2026-09-01') $$,
  'three times a week');
select is(
  (select owner_id from public.habits where id = 'bbbbbbbb-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the habit belongs to the signed-in user');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000004', 'No days', 'weekdays', '2026-09-01') $$,
  '23514', null,
  'a weekday habit names its days');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, times, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000005', 'Too often', 'per_week', 8, '2026-09-01') $$,
  '23514', null,
  'a week has at most seven days');
select throws_ok(
  $$ insert into public.habits (id, name, measure, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000006', 'Water', 'count', '2026-09-01') $$,
  '23514', null,
  'a count needs a target');
select throws_ok(
  $$ insert into public.habits (id, name, target, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000007', 'Checked', 3, '2026-09-01') $$,
  '23514', null,
  'a check has no target');
select throws_ok(
  $$ insert into public.habits (id, name, goal_id, starts_on) values ('bbbbbbbb-0000-0000-0000-000000000008', 'Sneaky', '99999999-0000-0000-0000-000000000001', '2026-09-01') $$,
  '23503', null,
  'a habit can''t serve someone else''s goal');
select lives_ok(
  $$ insert into public.habit_checkins (id, habit_id, day, value) values ('cccccccc-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002', '2026-09-18', 6.5) $$,
  'the owner checks in 6.5 km');
select throws_ok(
  $$ insert into public.habit_checkins (id, habit_id, day, value) values ('cccccccc-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', '2026-09-18', 1) $$,
  '23505', null,
  'a day has one check-in per habit');
select throws_ok(
  $$ insert into public.habit_checkins (id, habit_id, day, value) values ('cccccccc-0000-0000-0000-000000000003', '99999999-0000-0000-0000-000000000002', '2026-09-18', 1) $$,
  '23503', null,
  'a check-in can''t land on someone else''s habit');
select throws_ok(
  $$ insert into public.habit_checkins (id, habit_id, day, value) values ('cccccccc-0000-0000-0000-000000000004', 'bbbbbbbb-0000-0000-0000-000000000001', '2026-09-18', -1) $$,
  '23514', null,
  'a check-in is never negative');
select throws_ok(
  $$ insert into public.habit_pauses (id, habit_id, starts_on, ends_on) values ('dddddddd-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001', '2026-09-10', '2026-09-05') $$,
  '23514', null,
  'a pause ends after it starts');
select lives_ok(
  $$ insert into public.habit_pauses (id, habit_id, starts_on) values ('dddddddd-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000001', '2026-09-20') $$,
  'an open-ended pause');
select is((select count(*)::int from public.habits), 3, 'the owner sees only their own habits');

select lives_ok(
  $$ select public.undo_activity((select max(id) from public.activity_log where entity_id = 'cccccccc-0000-0000-0000-000000000001')) $$,
  'a check-in can be undone');

select * from finish();
rollback;

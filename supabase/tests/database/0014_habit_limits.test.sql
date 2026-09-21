-- Habits with a limit rather than a target (migration 0014).
begin;
create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.habits (id, name, cadence, measure, starts_on)
     values ('dddddddd-0000-0000-0000-000000000001', 'Read', 'daily', 'check', '2026-09-01') $$,
  'a habit still needs nothing said about its direction');
select is(
  (select direction from public.habits where id = 'dddddddd-0000-0000-0000-000000000001'),
  'at_least',
  'a habit without a direction is one to build');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, measure, target, unit, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000002', 'Snacks', 'daily', 'count', 2, 'snacks',
             '2026-09-01', 'at_most') $$,
  'a daily habit can be a limit');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, weekdays, measure, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000003', 'No beer on work nights', 'weekdays', 31, 'check',
             '2026-09-01', 'at_most') $$,
  'so can one on chosen weekdays');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, times, measure, target, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000004', 'Takeaway', 'per_week', 2, 'count', 1,
             '2026-09-01', 'at_most') $$,
  '23514', null,
  'a weekly habit cannot be a limit');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, measure, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000005', 'Odd', 'daily', 'check', '2026-09-01', 'sideways') $$,
  '23514', null,
  'a habit is at_least or at_most');

select * from finish();
rollback;

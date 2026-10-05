-- A limit for a week or a month, and a limit of none (migration 0024).
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.habits (id, name, cadence, times, measure, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000001', 'Takeaway', 'per_week', 2, 'check', '2026-10-05', 'at_most') $$,
  'a weekly check habit can be a limit: at most two days a week');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, times, measure, target, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000002', 'Drinks', 'per_month', 1, 'count', 5, '2026-10-05', 'at_most') $$,
  'a monthly count can be a limit on its total');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, times, measure, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000003', 'Casino', 'per_week', 0, 'check', '2026-10-05', 'at_most') $$,
  'a weekly limit can be no day at all');
select lives_ok(
  $$ insert into public.habits (id, name, cadence, measure, target, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000004', 'Cigarettes', 'daily', 'count', 0, '2026-10-05', 'at_most') $$,
  'a daily count can have a limit of 0');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, measure, target, starts_on)
     values ('dddddddd-0000-0000-0000-000000000005', 'Water', 'daily', 'count', 0, '2026-10-05') $$,
  '23514', null,
  'a habit to build still needs a target above 0');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, times, measure, starts_on)
     values ('dddddddd-0000-0000-0000-000000000006', 'Gym', 'per_week', 0, 'check', '2026-10-05') $$,
  '23514', null,
  'and at least one day a week');
select throws_ok(
  $$ insert into public.habits (id, name, cadence, times, measure, starts_on, direction)
     values ('dddddddd-0000-0000-0000-000000000007', 'Too many', 'per_week', 8, 'check', '2026-10-05', 'at_most') $$,
  '23514', null,
  'a week still has at most seven days');

select * from finish();
rollback;

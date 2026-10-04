-- Failed habit days (migration 0021).
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test'),
       ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'stranger@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.habits (id, name, cadence, measure, starts_on)
values ('dddddddd-0000-0000-0000-000000000001', 'Read', 'daily', 'check', '2026-09-01');

select lives_ok(
  $$ insert into public.habit_checkins (id, habit_id, day, value, failed)
     values ('eeeeeeee-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001', '2026-10-02', 0, true) $$,
  'the owner fails a day');
select is(
  (select failed from public.habit_checkins where id = 'eeeeeeee-0000-0000-0000-000000000001'),
  true,
  'the day is failed');
select is(
  (select count(*)::integer from public.activity_log where entity = 'habit_checkins'
     and entity_id = 'eeeeeeee-0000-0000-0000-000000000001' and after ->> 'failed' = 'true'),
  1,
  'failing a day is in the activity log, so it can be undone');

update public.habit_checkins set skipped = true where id = 'eeeeeeee-0000-0000-0000-000000000001';
select is(
  (select failed from public.habit_checkins where id = 'eeeeeeee-0000-0000-0000-000000000001'),
  false,
  'a skip takes the fail back');

update public.habit_checkins set skipped = false, failed = true where id = 'eeeeeeee-0000-0000-0000-000000000001';
update public.habit_checkins set value = 1 where id = 'eeeeeeee-0000-0000-0000-000000000001';
select is(
  (select failed from public.habit_checkins where id = 'eeeeeeee-0000-0000-0000-000000000001'),
  false,
  'a check-in, even from an app that leaves the column out, takes the fail back');

select throws_ok(
  $$ update public.habit_checkins set value = 0, failed = null where id = 'eeeeeeee-0000-0000-0000-000000000001' $$,
  '23502', null,
  'failed is true or false, never null');

-- A stranger's update finds no row under row security.
select set_config('request.jwt.claims',
  '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}', true);
update public.habit_checkins set value = 0, failed = true where id = 'eeeeeeee-0000-0000-0000-000000000001';

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select is(
  (select failed from public.habit_checkins where id = 'eeeeeeee-0000-0000-0000-000000000001'),
  false,
  'a stranger can''t fail the owner''s day');

select * from finish();
rollback;

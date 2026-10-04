-- Habit reminders (migration 0022).
begin;
create extension if not exists pgtap with schema extensions;
select plan(5);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test'),
       ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'stranger@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.habits (id, name, cadence, measure, starts_on)
     values ('dddddddd-0000-0000-0000-000000000001', 'Read', 'daily', 'check', '2026-09-01') $$,
  'a habit still needs no reminder');
select is(
  (select remind_at from public.habits where id = 'dddddddd-0000-0000-0000-000000000001'),
  null,
  'a new habit doesn''t remind');
select lives_ok(
  $$ update public.habits set remind_at = '21:00' where id = 'dddddddd-0000-0000-0000-000000000001' $$,
  'the owner gives it a time');
select is(
  (select count(*)::integer from public.activity_log where entity = 'habits'
     and entity_id = 'dddddddd-0000-0000-0000-000000000001' and action = 'update'
     and after ->> 'remind_at' = '21:00:00'),
  1,
  'the reminder time is in the activity log, so it can be undone');

-- A stranger's update finds no row under row security.
select set_config('request.jwt.claims',
  '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}', true);
update public.habits set remind_at = null where id = 'dddddddd-0000-0000-0000-000000000001';

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select is(
  (select remind_at from public.habits where id = 'dddddddd-0000-0000-0000-000000000001'),
  '21:00'::time,
  'a stranger can''t change the owner''s reminder');

select * from finish();
rollback;

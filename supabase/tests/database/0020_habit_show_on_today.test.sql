-- Habits kept off Today (migration 0020).
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

select lives_ok(
  $$ insert into public.habits (id, name, cadence, measure, starts_on)
     values ('dddddddd-0000-0000-0000-000000000001', 'Read', 'daily', 'check', '2026-09-01') $$,
  'a habit still needs nothing said about Today');
select is(
  (select show_on_today from public.habits where id = 'dddddddd-0000-0000-0000-000000000001'),
  true,
  'a new habit shows on Today');
select lives_ok(
  $$ update public.habits set show_on_today = false where id = 'dddddddd-0000-0000-0000-000000000001' $$,
  'the owner keeps it off Today');
select is(
  (select count(*)::integer from public.activity_log where entity = 'habits'
     and entity_id = 'dddddddd-0000-0000-0000-000000000001' and action = 'update'
     and after ->> 'show_on_today' = 'false'),
  1,
  'keeping it off Today is in the activity log, so it can be undone');
select throws_ok(
  $$ update public.habits set show_on_today = null where id = 'dddddddd-0000-0000-0000-000000000001' $$,
  '23502', null,
  'show_on_today is true or false, never null');

-- A stranger's update finds no row under row security.
select set_config('request.jwt.claims',
  '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}', true);
update public.habits set show_on_today = true where id = 'dddddddd-0000-0000-0000-000000000001';

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select is(
  (select show_on_today from public.habits where id = 'dddddddd-0000-0000-0000-000000000001'),
  false,
  'a stranger cannot put the owner''s habit back on Today');
select is(
  (select name from public.habits where id = 'dddddddd-0000-0000-0000-000000000001'),
  'Read',
  'keeping a habit off Today never touched the habit itself');

select * from finish();
rollback;

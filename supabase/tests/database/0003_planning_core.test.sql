-- Row security, server clock and integrity on the synced tables (migration 0003).
begin;
create extension if not exists pgtap with schema extensions;
select plan(24);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's own task, created as the test runner.
insert into public.tasks (id, owner_id, title)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Not yours');

-- Act as the owner.
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.areas (id, name, color) values ('bbbbbbbb-0000-0000-0000-000000000001', 'Health', 'coral') $$,
  'the owner creates an area without naming the owner');
select lives_ok(
  $$ insert into public.tasks (id, title, area_id, updated_at, created_at)
     values ('aaaaaaaa-0000-0000-0000-000000000001', 'Run', 'bbbbbbbb-0000-0000-0000-000000000001',
             '2000-01-01', '2026-09-01T08:00:00Z') $$,
  'the owner creates a task in that area');
select is(
  (select owner_id from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'a new row belongs to the signed-in user');
select is(
  (select updated_at from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  now(),
  'updated_at is the server clock, whatever the device sent');
select is(
  (select created_at from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '2026-09-01T08:00:00Z'::timestamptz,
  'created_at keeps the device''s creation time');

select lives_ok(
  $$ insert into public.tasks (id, title) values ('aaaaaaaa-0000-0000-0000-000000000001', 'Run 5 km')
     on conflict (id) do update set title = excluded.title $$,
  'a repeated push (upsert) updates the row');
select is(
  (select title from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  'Run 5 km',
  'the upsert applied');

update public.tasks
set owner_id = '22222222-2222-2222-2222-222222222222', created_at = '1999-01-01'
where id = 'aaaaaaaa-0000-0000-0000-000000000001';
select is(
  (select owner_id from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'a row can''t be handed to someone else');
select is(
  (select created_at from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '2026-09-01T08:00:00Z'::timestamptz,
  'created_at can''t be rewritten');

select lives_ok(
  $$ insert into public.task_steps (id, task_id, title) values
       ('cccccccc-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'Shoes') $$,
  'the owner adds a step to their task');
select lives_ok(
  $$ insert into public.tags (id, name) values ('dddddddd-0000-0000-0000-000000000001', 'outside') $$,
  'the owner creates a tag');
select lives_ok(
  $$ insert into public.task_tags (id, task_id, tag_id) values
       ('eeeeeeee-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001',
        'dddddddd-0000-0000-0000-000000000001') $$,
  'the owner tags their task');
select lives_ok(
  $$ insert into public.reminders (id, task_id, fire_at) values
       ('ffffffff-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', '2026-09-20T07:00:00Z') $$,
  'the owner sets a reminder');

select throws_ok(
  $$ insert into public.task_steps (id, task_id, title) values
       ('cccccccc-0000-0000-0000-000000000002', '99999999-0000-0000-0000-000000000001', 'Sneaky') $$,
  '23503', null, 'a step can''t hang off someone else''s task');
select throws_ok(
  $$ insert into public.tasks (id, owner_id, title) values
       ('aaaaaaaa-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'Planted') $$,
  '42501', null, 'the owner can''t create rows for someone else');
select throws_ok(
  $$ delete from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '42501', null, 'devices soft-delete; hard deletes are refused');
select throws_ok(
  $$ insert into public.tasks (id, title, planned_time) values ('aaaaaaaa-0000-0000-0000-000000000003', 'x', '10:00') $$,
  '23514', null, 'a time needs a day');
select throws_ok(
  $$ insert into public.tasks (id, title, status) values ('aaaaaaaa-0000-0000-0000-000000000004', 'x', 'done') $$,
  '23514', null, 'a done task needs its completion time');
select throws_ok(
  $$ insert into public.reminders (id, task_id) values ('ffffffff-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001') $$,
  '23514', null, 'a reminder is either fixed or relative');

select is((select count(*)::int from public.tasks), 1, 'the owner sees only their own tasks');

update public.tasks set title = 'Mine now' where id = '99999999-0000-0000-0000-000000000001';

select lives_ok(
  $$ update public.tasks set deleted_at = now() where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'the owner tombstones a task');
select is(
  (select count(*)::int from public.tasks where deleted_at is not null),
  1,
  'tombstones stay readable so other devices learn about the deletion');

reset role;
select is(
  (select title from public.tasks where id = '99999999-0000-0000-0000-000000000001'),
  'Not yours',
  'the owner could not change someone else''s task');

set local role anon;
select set_config('request.jwt.claims', '{"role": "anon"}', true);
select throws_ok($$ select * from public.tasks $$, '42501', null, 'anonymous callers get nothing');
reset role;

select * from finish();
rollback;

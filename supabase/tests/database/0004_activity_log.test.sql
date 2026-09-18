-- The server-written activity log (migration 0004).
begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.tasks (id, title) values ('aaaaaaaa-0000-0000-0000-000000000001', 'Run');
update public.tasks set title = 'Run 5 km' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.tasks set title = 'Run 5 km' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.tasks set deleted_at = now() where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.tasks set deleted_at = null where id = 'aaaaaaaa-0000-0000-0000-000000000001';

select results_eq(
  $$ select action from public.activity_log order by id $$,
  array['create', 'update', 'delete', 'restore'],
  'creates, edits, deletes and restores are logged in order; an unchanged re-push is not');
select is(
  (select before ->> 'title' from public.activity_log where action = 'update'),
  'Run',
  'an update keeps the row as it was, for undo');
select is(
  (select distinct actor from public.activity_log),
  'owner',
  'without the connector header, the owner is the actor');

select set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
update public.tasks set title = 'Run 10 km' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
select is(
  (select actor from public.activity_log order by id desc limit 1),
  'claude',
  'the connector''s header marks Claude as the actor');

select set_config('request.headers', '{"x-goalmaker-actor": "system"}', true);
update public.tasks set title = 'Run 12 km' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
select is(
  (select actor from public.activity_log order by id desc limit 1),
  'owner',
  'any other claimed actor counts as the owner');

select throws_ok(
  $$ insert into public.activity_log (owner_id, entity, entity_id, action, after)
     values ('11111111-1111-1111-1111-111111111111', 'tasks', gen_random_uuid(), 'create', '{}') $$,
  '42501', null, 'clients can''t write the log');
select throws_ok(
  $$ delete from public.activity_log $$,
  '42501', null, 'clients can''t erase the log');

select set_config('request.jwt.claims',
  '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}', true);
select is((select count(*)::int from public.activity_log), 0, 'a stranger sees none of the owner''s log');

reset role;
select is(
  (select count(*)::int from public.activity_log where owner_id = '11111111-1111-1111-1111-111111111111'),
  6,
  'the log holds every real change');

select * from finish();
rollback;

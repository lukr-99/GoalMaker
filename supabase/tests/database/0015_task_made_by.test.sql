-- Who made a task (migration 0015).
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.tasks (id, title) values ('aaaaaaaa-1500-0000-0000-000000000001', 'From an older app');
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000001'),
  'owner',
  'a task that doesn''t say who made it, without the connector header, is the owner''s');

select set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
insert into public.tasks (id, title) values ('aaaaaaaa-1500-0000-0000-000000000002', 'Claude''s idea');
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000002'),
  'claude',
  'a task that doesn''t say who made it, through the connector, is Claude''s');

insert into public.tasks (id, title, made_by) values ('aaaaaaaa-1500-0000-0000-000000000003', 'Dictated', 'owner');
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000003'),
  'owner',
  'the connector can say the owner made a task the owner asked for');

select set_config('request.headers', '{}', true);
insert into public.tasks (id, title, made_by) values ('aaaaaaaa-1500-0000-0000-000000000004', 'Next time', 'claude');
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000004'),
  'claude',
  'an app keeps who made a repeating task on its next occurrence');

update public.tasks set made_by = 'owner', title = 'Claude''s idea, renamed'
where id = 'aaaaaaaa-1500-0000-0000-000000000002';
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000002'),
  'claude',
  'an edit can''t change who made a task');

-- An entry logged before 0015 has no made_by in its snapshot, and undo writes every column back.
reset role;
update public.activity_log set before = before - 'made_by'
where id = (select max(id) from public.activity_log where entity_id = 'aaaaaaaa-1500-0000-0000-000000000002');
set local role authenticated;
select undo_activity((select max(id) from public.activity_log where entity_id = 'aaaaaaaa-1500-0000-0000-000000000002'));
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1500-0000-0000-000000000002'),
  'claude',
  'undoing a change logged before 0015 leaves who made the task alone');

select throws_ok(
  $$ insert into public.tasks (id, title, made_by) values ('aaaaaaaa-1500-0000-0000-000000000005', 'Odd', 'someone') $$,
  '23514', null, 'a task is made by the owner or Claude');

select * from finish();
rollback;

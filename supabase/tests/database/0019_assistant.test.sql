-- The quick chat: the activity log's via and the chat's limits (migration 0019).
begin;
create extension if not exists pgtap with schema extensions;
select plan(12);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

-- Via
insert into public.tasks (id, title) values ('aaaaaaaa-1900-0000-0000-000000000001', 'From the app');
select is(
  (select via from public.activity_log where entity_id = 'aaaaaaaa-1900-0000-0000-000000000001'),
  null,
  'a change from an app came through no chat');

select set_config('request.headers', '{"x-goalmaker-actor": "owner", "x-goalmaker-via": "chat"}', true);
insert into public.tasks (id, title) values ('aaaaaaaa-1900-0000-0000-000000000002', 'Asked in the chat');
select is(
  (select actor || ' via ' || via from public.activity_log where entity_id = 'aaaaaaaa-1900-0000-0000-000000000002'),
  'owner via chat',
  'a change through the chat is the owner''s, noted as the chat');
select is(
  (select made_by from public.tasks where id = 'aaaaaaaa-1900-0000-0000-000000000002'),
  'owner',
  'a task asked for in the chat is made by the owner');

select set_config('request.headers', '{"x-goalmaker-via": "somewhere"}', true);
insert into public.tasks (id, title) values ('aaaaaaaa-1900-0000-0000-000000000003', 'Odd header');
select is(
  (select via from public.activity_log where entity_id = 'aaaaaaaa-1900-0000-0000-000000000003'),
  null,
  'only the chat is ever noted');
select set_config('request.headers', '{}', true);

-- The usage table and its function are the Edge Function's alone.
select throws_ok($$ select * from public.assistant_usage $$, '42501', null, 'an owner can''t read the chat''s usage');
select throws_ok(
  $$ insert into public.assistant_usage (owner_id, minute_started_at, day)
     values ('11111111-1111-1111-1111-111111111111', now(), current_date) $$,
  '42501', null, 'an owner can''t write the chat''s usage');
select throws_ok(
  $$ select public.assistant_count('11111111-1111-1111-1111-111111111111', 100, 100) $$,
  '42501', null, 'an owner can''t count requests themselves');

-- The limits, as the Edge Function's database user.
reset role;
select is(
  array[public.assistant_count('11111111-1111-1111-1111-111111111111', 2, 10),
        public.assistant_count('11111111-1111-1111-1111-111111111111', 2, 10),
        public.assistant_count('11111111-1111-1111-1111-111111111111', 2, 10)],
  array['ok', 'ok', 'minute'],
  'the third request in a minute is refused');
select is(
  (select minute_calls || '/' || day_calls from public.assistant_usage
   where owner_id = '11111111-1111-1111-1111-111111111111'),
  '2/2',
  'a refused request is not counted');

update public.assistant_usage set minute_started_at = now() - interval '61 seconds'
where owner_id = '11111111-1111-1111-1111-111111111111';
select is(public.assistant_count('11111111-1111-1111-1111-111111111111', 2, 10), 'ok', 'a new minute starts over');

select is(
  array[public.assistant_count('22222222-2222-2222-2222-222222222222', 30, 1),
        public.assistant_count('22222222-2222-2222-2222-222222222222', 30, 1)],
  array['ok', 'day'],
  'the daily cap holds per owner, apart from another owner''s minute');

update public.assistant_usage set day = day - 1 where owner_id = '22222222-2222-2222-2222-222222222222';
select is(public.assistant_count('22222222-2222-2222-2222-222222222222', 30, 1), 'ok', 'a new day starts over');

select * from finish();
rollback;

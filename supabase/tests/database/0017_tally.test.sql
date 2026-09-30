-- Row security and integrity for Tally's days, rules and categories (migration 0017).
begin;
create extension if not exists pgtap with schema extensions;
select plan(21);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's project and day, made as the test runner.
insert into public.projects (id, owner_id, name)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs');
insert into public.tally_days (id, owner_id, day, device, device_kind, category, minutes)
values ('99999999-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', '2026-09-28',
        'd1e57000-0000-4000-8000-00000000bbbb', 'phone', 'video', 120);

-- ADR 0013: an app or a window's name may only ever be a rule's pattern, which the owner wrote.
select set_eq(
  $$ select column_name::text from information_schema.columns
     where table_schema = 'public' and table_name = 'tally_days' $$,
  array['id', 'owner_id', 'day', 'device', 'device_kind', 'category', 'project_id', 'minutes',
        'created_at', 'updated_at', 'deleted_at'],
  'a tally day holds minutes by category and project, never an app or a window');
select set_eq(
  $$ select column_name::text from information_schema.columns
     where table_schema = 'public' and table_name = 'tally_rules' and data_type = 'text' $$,
  array['match', 'pattern', 'platform', 'category'],
  'the only free text in a rule is the pattern the owner wrote, beside its category');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.projects (id, name) values ('aaaaaaaa-0000-0000-0000-000000000001', 'GoalMaker');

select lives_ok(
  $$ insert into public.tally_days (id, day, device, device_kind, category, project_id, minutes)
     values ('aaaaaaaa-0000-0000-0000-000000000002', '2026-09-28', 'd1e57000-0000-4000-8000-00000000aaaa',
             'pc', 'coding', 'aaaaaaaa-0000-0000-0000-000000000001', 95) $$,
  'a PC''s day counts minutes toward a project');
select is(
  (select owner_id from public.tally_days where id = 'aaaaaaaa-0000-0000-0000-000000000002'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the day belongs to the signed-in user');
select throws_ok(
  $$ insert into public.tally_days (id, day, device, device_kind, category, project_id, minutes)
     values ('aaaaaaaa-0000-0000-0000-000000000003', '2026-09-28', 'd1e57000-0000-4000-8000-00000000aaaa',
             'phone', 'coding', 'aaaaaaaa-0000-0000-0000-000000000001', 10) $$,
  '23514', null,
  'the phone never links time to a project');
select throws_ok(
  $$ insert into public.tally_days (id, day, device, device_kind, category, minutes)
     values ('aaaaaaaa-0000-0000-0000-000000000004', '2026-09-28', 'd1e57000-0000-4000-8000-00000000aaaa',
             'pc', 'coding', 1441) $$,
  '23514', null,
  'a day holds at most 1,440 minutes');
select throws_ok(
  $$ insert into public.tally_days (id, day, device, device_kind, category, minutes)
     values ('aaaaaaaa-0000-0000-0000-000000000005', '2026-09-28', 'd1e57000-0000-4000-8000-00000000aaaa',
             'tablet', 'coding', 5) $$,
  '23514', null,
  'a device is a phone or a PC');
select throws_ok(
  $$ insert into public.tally_days (id, day, device, device_kind, category, project_id, minutes)
     values ('aaaaaaaa-0000-0000-0000-000000000006', '2026-09-28', 'd1e57000-0000-4000-8000-00000000aaaa',
             'pc', 'coding', '99999999-0000-0000-0000-000000000001', 5) $$,
  '23503', null,
  'time can''t count toward someone else''s project');
select lives_ok(
  $$ update public.tally_days set minutes = 110 where id = 'aaaaaaaa-0000-0000-0000-000000000002' $$,
  'a device rewrites its day');
select is(
  (select count(*)::integer from public.activity_log where entity = 'tally_days'),
  0,
  'and that is not in the activity log');

select lives_ok(
  $$ insert into public.tally_categories (id, name, color, emoji) values ('bbbbbbbb-0000-0000-0000-000000000001', 'Music', 'lime', '🎵') $$,
  'the owner makes a category of their own');
select throws_ok(
  $$ insert into public.tally_categories (id, name) values ('bbbbbbbb-0000-0000-0000-000000000002', '') $$,
  '23514', null,
  'a category has a name');
select lives_ok(
  $$ insert into public.tally_rules (id, match, pattern, platform, category, project_id)
     values ('cccccccc-0000-0000-0000-000000000001', 'folder', 'GoalMaker', 'windows', 'coding',
             'aaaaaaaa-0000-0000-0000-000000000001') $$,
  'a rule sends an editor''s folder to a category and a project');
select throws_ok(
  $$ insert into public.tally_rules (id, match, pattern, platform, category)
     values ('cccccccc-0000-0000-0000-000000000002', 'title', 'YouTube', 'android', 'video') $$,
  '23514', null,
  'Android has no window titles, so a title rule can''t be Android''s');
select throws_ok(
  $$ insert into public.tally_rules (id, match, pattern, platform, category)
     values ('cccccccc-0000-0000-0000-000000000003', 'url', 'youtube.com', 'any', 'video') $$,
  '23514', null,
  'a rule matches an app, a title or a folder');
select throws_ok(
  $$ insert into public.tally_rules (id, match, pattern, platform, category)
     values ('cccccccc-0000-0000-0000-000000000004', 'app', '   ', 'any', 'video') $$,
  '23514', null,
  'a rule has a pattern');
select is(
  (select count(*)::integer from public.activity_log where entity in ('tally_rules', 'tally_categories')),
  2,
  'the owner''s rules and categories are in the activity log, so they can be undone');

select is(
  (select count(*)::integer from public.tally_days where id = '99999999-0000-0000-0000-000000000002'),
  0,
  'a stranger''s day is invisible');
select is_empty(
  $$ update public.tally_days set minutes = 1 where id = '99999999-0000-0000-0000-000000000002' returning id $$,
  'and can''t be changed');
select throws_ok(
  $$ delete from public.tally_days where id = 'aaaaaaaa-0000-0000-0000-000000000002' $$,
  '42501', null,
  'rows are never deleted, only marked deleted');

reset role;
select set_config('request.jwt.claims', '', true);
set local role anon;
select throws_ok(
  $$ select count(*) from public.tally_days $$,
  '42501', null,
  'nobody signed out reads Tally');

select * from finish();
rollback;

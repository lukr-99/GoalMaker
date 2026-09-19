-- Row security and integrity for ritual runs, and archiving areas (migration 0006).
begin;
create extension if not exists pgtap with schema extensions;
select plan(12);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's own run, created as the test runner.
insert into public.ritual_runs (id, owner_id, ritual, day)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'plan_tomorrow', '2026-09-18');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.ritual_runs (id, ritual, day) values ('cccccccc-0000-0000-0000-000000000001', 'plan_tomorrow', '2026-09-18') $$,
  'the owner records a run without naming the owner');
select is(
  (select owner_id from public.ritual_runs where id = 'cccccccc-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the run belongs to the signed-in user');
select is(
  (select outcome from public.ritual_runs where id = 'cccccccc-0000-0000-0000-000000000001'),
  'done',
  'a run is done unless it says it was skipped');
select lives_ok(
  $$ insert into public.ritual_runs (id, ritual, day, outcome) values ('cccccccc-0000-0000-0000-000000000001', 'plan_tomorrow', '2026-09-18', 'skipped')
     on conflict (id) do update set outcome = excluded.outcome $$,
  'the other device pushing the same run merges into it');
select throws_ok(
  $$ insert into public.ritual_runs (id, ritual, day) values ('cccccccc-0000-0000-0000-000000000002', 'plan_tomorrow', '2026-09-18') $$,
  '23505', null,
  'a second run for the same ritual and day is refused');
select throws_ok(
  $$ insert into public.ritual_runs (id, ritual, day) values ('cccccccc-0000-0000-0000-000000000003', 'nap', '2026-09-18') $$,
  '23514', null,
  'only known rituals');
select throws_ok(
  $$ insert into public.ritual_runs (id, ritual, day, outcome) values ('cccccccc-0000-0000-0000-000000000004', 'weekly_review', '2026-09-20', 'maybe') $$,
  '23514', null,
  'only done or skipped');
select is((select count(*)::int from public.ritual_runs), 1, 'the owner sees only their own runs');

select lives_ok(
  $$ insert into public.areas (id, name, color) values ('bbbbbbbb-0000-0000-0000-000000000001', 'School', 'blue') $$,
  'the owner creates an area');
select lives_ok(
  $$ update public.areas set archived_at = now() where id = 'bbbbbbbb-0000-0000-0000-000000000001' $$,
  'the owner archives it');
select isnt(
  (select archived_at from public.areas where id = 'bbbbbbbb-0000-0000-0000-000000000001'),
  null,
  'archived_at is kept');

reset role;
select is(
  (select count(*)::int from public.activity_log
   where entity = 'ritual_runs' and entity_id = 'cccccccc-0000-0000-0000-000000000001'),
  2,
  'recording and merging a run are both in the activity log');

select * from finish();
rollback;

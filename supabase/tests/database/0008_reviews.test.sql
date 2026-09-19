-- Row security and integrity for reviews (migration 0008).
begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's review, made as the test runner.
insert into public.reviews (id, owner_id, kind, period_start, summary)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'weekly', '2026-09-14', 'Theirs');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.reviews (id, kind, period_start, summary) values ('dddddddd-0000-0000-0000-000000000001', 'weekly', '2026-09-14', 'A good week') $$,
  'the owner saves a weekly review');
select is(
  (select owner_id from public.reviews where id = 'dddddddd-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the review belongs to the signed-in user');
select lives_ok(
  $$ insert into public.reviews (id, kind, period_start, summary, mood) values ('dddddddd-0000-0000-0000-000000000001', 'weekly', '2026-09-14', 'A great week', 5)
     on conflict (id) do update set summary = excluded.summary, mood = excluded.mood $$,
  'saving the same review again updates it');
select throws_ok(
  $$ insert into public.reviews (id, kind, period_start) values ('dddddddd-0000-0000-0000-000000000002', 'weekly', '2026-09-14') $$,
  '23505', null,
  'a second review for the same kind and period is refused');
select throws_ok(
  $$ insert into public.reviews (id, kind, period_start) values ('dddddddd-0000-0000-0000-000000000003', 'weekly', '2026-09-16') $$,
  '23514', null,
  'a week starts on its Monday');
select throws_ok(
  $$ insert into public.reviews (id, kind, period_start) values ('dddddddd-0000-0000-0000-000000000004', 'monthly', '2026-09-02') $$,
  '23514', null,
  'a month starts on its first day');
select throws_ok(
  $$ insert into public.reviews (id, kind, period_start, mood) values ('dddddddd-0000-0000-0000-000000000005', 'monthly', '2026-09-01', 6) $$,
  '23514', null,
  'mood is 1 to 5');
select is((select count(*)::int from public.reviews), 1, 'the owner sees only their own reviews');

reset role;
select is(
  (select actor from public.activity_log where entity = 'reviews' and entity_id = 'dddddddd-0000-0000-0000-000000000001' order by id limit 1),
  'owner',
  'saving a review is in the activity log');

select * from finish();
rollback;

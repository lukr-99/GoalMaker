-- Row security and integrity for wants and their cooldown thresholds (migration 0016).
begin;
create extension if not exists pgtap with schema extensions;
select plan(20);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's want and area, made as the test runner.
insert into public.areas (id, owner_id, name, color)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'red');
insert into public.wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until, made_by)
values ('99999999-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'Their boat',
        'Sailing', 90, '2026-09-28', '2026-12-27', 'owner');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.wants (id, title, reason, price, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000001', 'Trail shoes', 'The old ones have holes', 3400, 30,
             '2026-09-28', '2026-10-28') $$,
  'a want needs a title, a reason and its cooldown');
select is(
  (select owner_id from public.wants where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the want belongs to the signed-in user');
select is(
  (select currency || ' ' || made_by from public.wants where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  'CZK owner',
  'it is in crowns and the owner''s, unless it says otherwise');
select throws_ok(
  $$ insert into public.wants (id, title, reason, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000009', 'Reasonless', '', 7, '2026-09-28', '2026-10-05') $$,
  '23514', null,
  'a want without a reason is refused');
select throws_ok(
  $$ insert into public.wants (id, title, reason, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000008', 'Off by one', 'Why not', 7, '2026-09-28', '2026-10-06') $$,
  '23514', null,
  'it cools exactly its days after it was added');
select throws_ok(
  $$ insert into public.wants (id, title, reason, price, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000007', 'Refund', 'Money back', -5, 7, '2026-09-28', '2026-10-05') $$,
  '23514', null,
  'a price is never negative');
select throws_ok(
  $$ insert into public.wants (id, title, reason, currency, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000006', 'Odd money', 'Why not', 'crowns', 7, '2026-09-28', '2026-10-05') $$,
  '23514', null,
  'a currency is three capital letters');
select throws_ok(
  $$ insert into public.wants (id, title, reason, area_id, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000005', 'Sneaky', 'Why not', '99999999-0000-0000-0000-000000000001',
             7, '2026-09-28', '2026-10-05') $$,
  '23503', null,
  'a want can''t go in someone else''s area');

select throws_ok(
  $$ update public.wants set decision = 'bought' where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '23514', null,
  'a decision comes with when it was made');
select lives_ok(
  $$ update public.wants set decision = 'dropped', decided_at = now(), decision_note = 'Fixed the old pair'
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'a want is dropped with a note');
select lives_ok(
  $$ update public.wants set decision = null, decided_at = null where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'and reopened');
select throws_ok(
  $$ update public.wants set checked_price = 2990 where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '23514', null,
  'a checked price says when it was checked');
select lives_ok(
  $$ update public.wants set checked_price = 2990, checked_at = now(), checked_note = 'Cheaper at the outlet'
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'Claude''s price check is kept');
select is(
  (select made_by from public.wants where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  'owner',
  'who added a want never changes');

select is(
  (select count(*)::integer from public.wants where id = '99999999-0000-0000-0000-000000000002'),
  0,
  'a stranger''s want is invisible');
select is_empty(
  $$ update public.wants set title = 'Mine now' where id = '99999999-0000-0000-0000-000000000002' returning id $$,
  'and can''t be changed');

select lives_ok(
  $$ insert into public.want_cooldowns (id, small_under, small_days) values ('bbbbbbbb-0000-0000-0000-000000000001', 2000, 3) $$,
  'the owner keeps their own thresholds');
select throws_ok(
  $$ insert into public.want_cooldowns (id) values ('bbbbbbbb-0000-0000-0000-000000000002') $$,
  '23505', null,
  'one row of thresholds per owner');
select throws_ok(
  $$ update public.want_cooldowns set medium_under = 500 where id = 'bbbbbbbb-0000-0000-0000-000000000001' $$,
  '23514', null,
  'the medium threshold is not below the small one');

reset role;
select set_config('request.jwt.claims', '', true);
set local role anon;
select throws_ok(
  $$ select count(*) from public.wants $$,
  '42501', null,
  'nobody signed out reads wants');

select * from finish();
rollback;

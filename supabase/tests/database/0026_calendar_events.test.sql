-- Row security and integrity for calendar events (migration 0026).
begin;
create extension if not exists pgtap with schema extensions;
select plan(16);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's area and event, made as the test runner.
insert into public.areas (id, owner_id, name, color)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'red');
insert into public.events (id, owner_id, title, starts_on, ends_on, made_by)
values ('99999999-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'Their holiday',
        '2026-12-20', '2026-12-31', 'owner');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.events (id, title, starts_on, ends_on, notes)
     values ('aaaaaaaa-0000-0000-0000-000000000001', 'Trip to Rome', '2026-10-30', '2026-11-02',
             'Flights on Friday') $$,
  'an event needs a title, a first day and a last day');
select is(
  (select owner_id from public.events where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the event belongs to the signed-in user');
select is(
  (select made_by from public.events where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  'owner',
  'and is the owner''s');
select lives_ok(
  $$ update public.events set ends_on = '2026-11-03', made_by = 'claude'
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'the owner moves the last day');
select is(
  (select ends_on::text || ' ' || made_by from public.events where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '2026-11-03 owner',
  'and who added it stays as it was');
select lives_ok(
  $$ insert into public.events (id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000002', 'A whole year', '2028-01-01', '2029-01-01') $$,
  'the last day may be 366 days after the first');

select throws_ok(
  $$ insert into public.events (id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000009', '', '2026-10-30', '2026-10-30') $$,
  '23514', null,
  'an event without a title is refused');
select throws_ok(
  $$ insert into public.events (id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000008', repeat('x', 201), '2026-10-30', '2026-10-30') $$,
  '23514', null,
  'and so is a title of 201 characters');
select throws_ok(
  $$ insert into public.events (id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000007', 'Backwards', '2026-10-30', '2026-10-29') $$,
  '23514', 'new row for relation "events" violates check constraint "events_end_not_before_start"',
  'an event can''t end before it starts');
select throws_ok(
  $$ insert into public.events (id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000006', 'Too long', '2026-01-01', '2027-01-03') $$,
  '23514', 'new row for relation "events" violates check constraint "events_at_most_a_year"',
  'or have its last day 367 days after the first');
select throws_ok(
  $$ insert into public.events (id, title, starts_on, ends_on, area_id)
     values ('aaaaaaaa-0000-0000-0000-000000000005', 'Sneaky', '2026-10-30', '2026-10-30',
             '99999999-0000-0000-0000-000000000001') $$,
  '23503', null,
  'an event can''t go in someone else''s area');

select is(
  (select count(*)::integer from public.events where id = '99999999-0000-0000-0000-000000000002'),
  0,
  'a stranger''s event is invisible');
select is_empty(
  $$ update public.events set title = 'Mine now' where id = '99999999-0000-0000-0000-000000000002' returning id $$,
  'and can''t be changed');
select throws_ok(
  $$ insert into public.events (id, owner_id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'Planted',
             '2026-10-30', '2026-10-30') $$,
  '42501', null,
  'nor added for them');

reset role;
select set_config('request.jwt.claims', '', true);
set local role anon;
select throws_ok(
  $$ select count(*) from public.events $$,
  '42501', null,
  'nobody signed out reads events');
select throws_ok(
  $$ insert into public.events (id, title, starts_on, ends_on)
     values ('aaaaaaaa-0000-0000-0000-000000000003', 'Anon', '2026-10-30', '2026-10-30') $$,
  '42501', null,
  'or adds one');

select * from finish();
rollback;

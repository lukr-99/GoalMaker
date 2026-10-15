-- PC sign-ins by QR code (migration 0028, ADR 0020).
begin;
create extension if not exists pgtap with schema extensions;
select plan(45);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test'),
  ('33333333-3333-3333-3333-333333333333', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', null);

create temporary table made (n int, id uuid, scan text, pickup text, match smallint) on commit drop;
grant all on made to anon, authenticated;

-- Started as the Edge Function would, through the service role, each from a caller of its own.
insert into made select 1, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('DESKTOP-1', repeat('1', 64));
insert into made select 2, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('  LAPTOP  ', repeat('2', 64));
insert into made select 3, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('OLD-PC', repeat('3', 64));
insert into made select 5, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('PHISHED-PC', repeat('5', 64));
insert into made select 6, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('NO-EMAIL-PC', repeat('6', 64));

select matches((select scan from made where n = 1), '^[A-Za-z0-9_-]{43}$', 'a scan secret is 43 URL-safe characters');
select matches((select pickup from made where n = 1), '^[A-Za-z0-9_-]{43}$', 'a pick-up secret is 43 URL-safe characters');
select isnt((select scan from made where n = 1), (select pickup from made where n = 1), 'the two secrets differ');
select is(
  (select scan_hash from public.pc_sign_ins where id = (select id from made where n = 1)),
  encode(extensions.digest((select scan from made where n = 1), 'sha256'), 'hex'),
  'only the SHA-256 hash of the scan secret is stored');
select is(
  (select expires_at - created_at from public.pc_sign_ins where id = (select id from made where n = 1)),
  interval '2 minutes',
  'a request lives two minutes');
select is((select device from public.pc_sign_ins where id = (select id from made where n = 2)), 'LAPTOP', 'the name is trimmed');
select ok((select match between 10 and 99 from made where n = 1), 'the match number has two digits');
select ok(
  (select cardinality(match_choices) = 3 and match_number = any (match_choices)
          and (select count(distinct c) from unnest(match_choices) c) = 3
   from public.pc_sign_ins where id = (select id from made where n = 1)),
  'the phone gets three different numbers, one of them the match');
select throws_ok(
  $$ select public.pc_sign_in_start('', repeat('9', 64)) $$,
  '23514', null,
  'a request needs a name');

-- Row security: nobody but the functions touches the tables.
set local role anon;
select throws_ok($$ select * from public.pc_sign_ins $$, '42501', null, 'anon can''t read requests');
select throws_ok($$ select * from public.pc_sign_in_callers $$, '42501', null, 'anon can''t read the call counts');
select throws_ok($$ select public.pc_sign_in_start('X', repeat('9', 64)) $$, '42501', null, 'anon can''t start a request directly');
select throws_ok($$ select public.pc_sign_in_allow(repeat('9', 64), 'start', 5) $$, '42501', null, 'anon can''t count calls');
select throws_ok(
  $$ select public.pc_sign_in_answer((select scan from made where n = 1), true, (select match from made where n = 1)) $$,
  '42501', null,
  'anon can''t approve');
select throws_ok(
  $$ select * from public.pc_sign_in_look((select scan from made where n = 1)) $$,
  '42501', null,
  'anon can''t look a request up');
reset role;

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select throws_ok($$ select * from public.pc_sign_ins $$, '42501', null, 'the owner can''t read requests');
select throws_ok(
  $$ insert into public.pc_sign_ins (scan_hash, pickup_hash, caller_hash, device, match_number, match_choices, expires_at)
     values (repeat('a', 64), repeat('b', 64), repeat('c', 64), 'X', 11, '{11,12,13}', now() + interval '1 hour') $$,
  '42501', null,
  'the owner can''t make requests');
select throws_ok(
  $$ select * from public.pc_sign_in_pick_up((select id from made where n = 1), (select pickup from made where n = 1)) $$,
  '42501', null,
  'the owner can''t pick up a request');
select throws_ok(
  $$ select public.pc_sign_in_finish((select id from made where n = 1), (select pickup from made where n = 1), true) $$,
  '42501', null,
  'the owner can''t finish a pick-up');
select is(
  (select device from public.pc_sign_in_look((select scan from made where n = 1))),
  'DESKTOP-1',
  'the phone sees which PC asked');
select ok(
  (select (select match from made where n = 1) = any (choices) and choices = (select array_agg(c order by c) from unnest(choices) c)
   from public.pc_sign_in_look((select scan from made where n = 1))),
  'the phone sees the match among its numbers, in order');
select is(
  (select count(*)::int from public.pc_sign_in_look((select pickup from made where n = 1))),
  0,
  'the pick-up secret is not a scan secret');
reset role;

select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 1), (select pickup from made where n = 1))),
  'waiting',
  'an unanswered request is waiting');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 1), (select scan from made where n = 1))),
  'expired',
  'the scan secret can''t pick up');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select is(
  public.pc_sign_in_answer((select scan from made where n = 5), true,
    (select case when match = 10 then 11 else 10 end from made where n = 5)::smallint),
  'mismatch',
  'a wrong number is refused');
select is(public.pc_sign_in_answer((select scan from made where n = 5), true, (select match from made where n = 5)), 'gone', 'and burns the request');
select is(public.pc_sign_in_answer((select scan from made where n = 1), true, null), 'mismatch', 'approving needs a number');
reset role;
insert into made select 7, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('DESKTOP-1', repeat('1', 64));
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select is(public.pc_sign_in_answer((select scan from made where n = 7), true, (select match from made where n = 7)), 'approved', 'the owner approves with the PC''s number');
select is(public.pc_sign_in_answer((select scan from made where n = 7), true, (select match from made where n = 7)), 'gone', 'a request is answered once');
select is(public.pc_sign_in_answer((select scan from made where n = 2), false), 'declined', 'the owner declines another');
select set_config('request.jwt.claims',
  '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}', true);
select is(public.pc_sign_in_answer((select scan from made where n = 6), true, (select match from made where n = 6)), 'no_email', 'an owner without an address can''t approve');
reset role;

select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 5), (select pickup from made where n = 5))),
  'declined',
  'the PC of a wrong number reads declined');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 6), (select pickup from made where n = 6))),
  'waiting',
  'a refused no-address approval leaves the request waiting');

-- Picking up: claimed, then used up once the code is made, or let go when it wasn't.
select results_eq(
  $$ select state, owner_id, email from public.pc_sign_in_pick_up((select id from made where n = 7), (select pickup from made where n = 7)) $$,
  $$ values ('approved', '11111111-1111-1111-1111-111111111111'::uuid, 'owner@example.test') $$,
  'the PC claims who approved it');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 7), (select pickup from made where n = 7))),
  'waiting',
  'a claimed approval is not handed out twice');
select is(public.pc_sign_in_finish((select id from made where n = 7), (select pickup from made where n = 7), false), true, 'no code made: the claim is let go');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 7), (select pickup from made where n = 7))),
  'approved',
  'so the approval is not lost');
select is(public.pc_sign_in_finish((select id from made where n = 7), (select pickup from made where n = 7), true), true, 'the code was made: used up');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 7), (select pickup from made where n = 7))),
  'expired',
  'an approval is picked up once');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 2), (select pickup from made where n = 2))),
  'declined',
  'a declined request says so');

-- Time: an expired request can't be approved, and an approval not picked up in a minute runs out.
update public.pc_sign_ins set created_at = now() - interval '3 minutes', expires_at = now() - interval '1 minute'
where id = (select id from made where n = 3);
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}', true);
select is(public.pc_sign_in_answer((select scan from made where n = 3), true, (select match from made where n = 3)), 'gone', 'an expired request can''t be approved');
reset role;
insert into made select 4, id, scan_secret, pickup_secret, match_number from public.pc_sign_in_start('SLOW-PC', repeat('4', 64));
update public.pc_sign_ins
set approved_by = '22222222-2222-2222-2222-222222222222', answered_at = now() - interval '61 seconds'
where id = (select id from made where n = 4);
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 4), (select pickup from made where n = 4))),
  'expired',
  'an approval left more than a minute runs out');

-- Limits: one caller holds at most three open requests, and calls are counted per minute.
select is((select count(*)::int from public.pc_sign_in_start('MORE', repeat('8', 64))), 1, 'a caller can start');
select is(
  (select count(*)::int from public.pc_sign_in_start('MORE', repeat('8', 64)))
    + (select count(*)::int from public.pc_sign_in_start('MORE', repeat('8', 64)))
    + (select count(*)::int from public.pc_sign_in_start('MORE', repeat('8', 64))),
  2,
  'but not a fourth open request at once');
select is(
  array[public.pc_sign_in_allow(repeat('8', 64), 'pick_up', 2), public.pc_sign_in_allow(repeat('8', 64), 'pick_up', 2), public.pc_sign_in_allow(repeat('8', 64), 'pick_up', 2)],
  array[true, true, false],
  'the call over the per-minute limit is refused');

select * from finish();
rollback;

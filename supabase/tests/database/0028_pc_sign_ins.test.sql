-- PC sign-ins by QR code (migration 0028, ADR 0020).
begin;
create extension if not exists pgtap with schema extensions;
select plan(27);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

create temporary table made (n int, id uuid, scan text, pickup text) on commit drop;
grant all on made to anon, authenticated;

-- Started as the Edge Function would, through the service role.
insert into made select 1, id, scan_secret, pickup_secret from public.pc_sign_in_start('DESKTOP-1');
insert into made select 2, id, scan_secret, pickup_secret from public.pc_sign_in_start('  LAPTOP  ');
insert into made select 3, id, scan_secret, pickup_secret from public.pc_sign_in_start('OLD-PC');

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
select throws_ok(
  $$ select public.pc_sign_in_start('') $$,
  '23514', null,
  'a request needs a name');

-- Row security: nobody but the functions touches the table.
set local role anon;
select throws_ok($$ select * from public.pc_sign_ins $$, '42501', null, 'anon can''t read requests');
select throws_ok($$ select public.pc_sign_in_start('X') $$, '42501', null, 'anon can''t start a request directly');
select throws_ok(
  $$ select public.pc_sign_in_answer((select scan from made where n = 1), true) $$,
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
  $$ insert into public.pc_sign_ins (scan_hash, pickup_hash, device, expires_at) values (repeat('a', 64), repeat('b', 64), 'X', now() + interval '1 hour') $$,
  '42501', null,
  'the owner can''t make requests');
select throws_ok(
  $$ select * from public.pc_sign_in_pick_up((select id from made where n = 1), (select pickup from made where n = 1)) $$,
  '42501', null,
  'the owner can''t pick up a request');
select is(
  (select device from public.pc_sign_in_look((select scan from made where n = 1))),
  'DESKTOP-1',
  'the phone sees which PC asked');
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
select is(public.pc_sign_in_answer((select scan from made where n = 1), true), true, 'the owner approves');
select is(public.pc_sign_in_answer((select scan from made where n = 1), true), false, 'a request is answered once');
select is(public.pc_sign_in_answer((select scan from made where n = 2), false), true, 'the owner declines another');
reset role;

select results_eq(
  $$ select state, owner_id, email from public.pc_sign_in_pick_up((select id from made where n = 1), (select pickup from made where n = 1)) $$,
  $$ values ('approved', '11111111-1111-1111-1111-111111111111'::uuid, 'owner@example.test') $$,
  'the PC picks up who approved it');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 1), (select pickup from made where n = 1))),
  'expired',
  'an approval is picked up once');
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 2), (select pickup from made where n = 2))),
  'declined',
  'a declined request says so');

-- A request that ran out can't be approved, and an approval not picked up in a minute runs out.
update public.pc_sign_ins set created_at = now() - interval '3 minutes', expires_at = now() - interval '1 minute'
where id = (select id from made where n = 3);
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}', true);
select is(public.pc_sign_in_answer((select scan from made where n = 3), true), false, 'an expired request can''t be approved');
reset role;
insert into made select 4, id, scan_secret, pickup_secret from public.pc_sign_in_start('SLOW-PC');
update public.pc_sign_ins
set approved_by = '22222222-2222-2222-2222-222222222222', answered_at = now() - interval '61 seconds'
where id = (select id from made where n = 4);
select is(
  (select state from public.pc_sign_in_pick_up((select id from made where n = 4), (select pickup from made where n = 4))),
  'expired',
  'an approval left more than a minute runs out');

-- Too many open requests: the next start gets nothing.
select is((select count(*)::int from public.pc_sign_in_start('ONE-MORE', interval '2 minutes', 1)), 0, 'starting stops while too many are open');

select * from finish();
rollback;

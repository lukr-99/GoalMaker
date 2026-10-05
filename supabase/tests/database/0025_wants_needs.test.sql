-- Needs beside wants (migration 0025).
begin;
create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.wants (id, title, reason, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000001', 'Kindle', 'Reading at night', 30, '2026-10-05', '2026-11-04') $$,
  'a want still goes in as before');
select is(
  (select kind from public.wants where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  'want',
  'and is a want');
select lives_ok(
  $$ insert into public.wants (id, title, reason, kind, need_by, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000002', 'Winter tyres', '', 'need', '2026-11-01', 0, '2026-10-05', '2026-10-05') $$,
  'a need may leave the reason empty and have a day it is needed by');
select throws_ok(
  $$ insert into public.wants (id, title, reason, kind, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000003', 'Printer ink', '', 'need', 7, '2026-10-05', '2026-10-12') $$,
  '23514', null,
  'a need skips the cooldown');
select throws_ok(
  $$ insert into public.wants (id, title, reason, need_by, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000004', 'Lamp', 'Dark desk', '2026-11-01', 7, '2026-10-05', '2026-10-12') $$,
  '23514', null,
  'only a need has a day it is needed by');
select throws_ok(
  $$ insert into public.wants (id, title, reason, cooldown_days, added_on, cools_until)
     values ('aaaaaaaa-0000-0000-0000-000000000005', 'Boat', '', 7, '2026-10-05', '2026-10-12') $$,
  '23514', null,
  'a want still says why');

select * from finish();
rollback;

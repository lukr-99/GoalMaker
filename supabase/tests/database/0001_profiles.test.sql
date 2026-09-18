-- Row security and grants on public.profiles (migration 0001).
begin;
create extension if not exists pgtap with schema extensions;
select plan(11);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

select is(
  (select count(*)::int from public.profiles
   where id in ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')),
  2,
  'a profile is created for every new user');

select is(
  (select day_rollover_hour from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
  4::smallint,
  'the day rolls over at 04:00 by default');

-- Act as the owner.
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select is((select count(*)::int from public.profiles), 1, 'the owner sees only their own profile');

update public.profiles set display_name = 'Owner' where id = '11111111-1111-1111-1111-111111111111';
select is(
  (select display_name from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
  'Owner',
  'the owner can change their settings');

update public.profiles set display_name = 'Changed by someone else'
where id = '22222222-2222-2222-2222-222222222222';

select throws_ok(
  $$ update public.profiles set created_at = now() where id = '11111111-1111-1111-1111-111111111111' $$,
  '42501', null, 'the owner cannot change server-managed columns');

select throws_ok(
  $$ insert into public.profiles (id) values ('33333333-3333-3333-3333-333333333333') $$,
  '42501', null, 'clients cannot create profiles');

select throws_ok(
  $$ delete from public.profiles where id = '11111111-1111-1111-1111-111111111111' $$,
  '42501', null, 'clients cannot delete profiles');

-- Back to the test runner to inspect what really happened.
reset role;

select is(
  (select display_name from public.profiles where id = '22222222-2222-2222-2222-222222222222'),
  null,
  'the owner could not change another user''s profile');

select isnt(
  (select updated_at from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
  null,
  'updated_at is stamped by the server');

-- Act as an anonymous caller.
set local role anon;
select set_config('request.jwt.claims', '{"role": "anon"}', true);

select throws_ok(
  $$ select * from public.profiles $$,
  '42501', null, 'anonymous callers cannot read profiles');

reset role;

select policies_are('public', 'profiles',
  array['profiles: owner reads', 'profiles: owner updates'],
  'profiles has exactly the owner policies');

select * from finish();
rollback;

-- Row security and integrity for life goals, their pictures and the pictures bucket (migration 0021).
begin;
create extension if not exists pgtap with schema extensions;
select plan(19);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's area, life goal, picture and file, made as the test runner.
insert into public.areas (id, owner_id, name, color)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'red');
insert into public.life_goals (id, owner_id, title, why, made_by)
values ('99999999-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'Their island',
        'Quiet', 'owner');
insert into public.life_goal_pictures (id, owner_id, life_goal_id, width, height)
values ('99999999-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
        '99999999-0000-0000-0000-000000000002', 800, 600);
insert into storage.objects (bucket_id, name, owner_id)
values ('life-goal-pictures', '22222222-2222-2222-2222-222222222222/99999999-0000-0000-0000-000000000003.jpg',
        '22222222-2222-2222-2222-222222222222');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.life_goals (id, title, why, by_date)
     values ('aaaaaaaa-0000-0000-0000-000000000001', 'Own an Audi R8', 'Proof that the work paid off',
             '2036-10-04') $$,
  'a life goal needs a title and a why');
select is(
  (select owner_id from public.life_goals where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the life goal belongs to the signed-in user');
select is(
  (select status || ' ' || made_by from public.life_goals where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  'open owner',
  'it starts open and the owner''s');
select throws_ok(
  $$ insert into public.life_goals (id, title, why) values ('aaaaaaaa-0000-0000-0000-000000000009', 'Whyless', '') $$,
  '23514', null,
  'a life goal without a why is refused');
select throws_ok(
  $$ insert into public.life_goals (id, title, why, area_id)
     values ('aaaaaaaa-0000-0000-0000-000000000008', 'Sneaky', 'Why not', '99999999-0000-0000-0000-000000000001') $$,
  '23503', null,
  'a life goal can''t go in someone else''s area');

select throws_ok(
  $$ update public.life_goals set status = 'achieved' where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '23514', null,
  'achieving one says when');
select lives_ok(
  $$ update public.life_goals set status = 'achieved', closed_at = now() where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'a life goal is achieved');
select lives_ok(
  $$ update public.life_goals set status = 'open', closed_at = null where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'and reopened');

select lives_ok(
  $$ insert into public.life_goal_pictures (id, life_goal_id, width, height)
     values ('aaaaaaaa-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 1600, 900) $$,
  'a life goal gets a picture');
select throws_ok(
  $$ insert into public.life_goal_pictures (id, life_goal_id, width, height)
     values ('aaaaaaaa-0000-0000-0000-000000000003', '99999999-0000-0000-0000-000000000002', 1600, 900) $$,
  '23503', null,
  'a picture can''t go on someone else''s life goal');
select throws_ok(
  $$ insert into public.life_goal_pictures (id, life_goal_id, width, height)
     values ('aaaaaaaa-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000001', 0, 900) $$,
  '23514', null,
  'a picture has a size');

select is(
  (select count(*)::integer from public.life_goals where id = '99999999-0000-0000-0000-000000000002'),
  0,
  'a stranger''s life goal is invisible');
select is(
  (select count(*)::integer from public.life_goal_pictures where id = '99999999-0000-0000-0000-000000000003'),
  0,
  'and so is their picture');
select is_empty(
  $$ update public.life_goals set title = 'Mine now' where id = '99999999-0000-0000-0000-000000000002' returning id $$,
  'and can''t be changed');

select lives_ok(
  $$ insert into storage.objects (bucket_id, name, owner_id)
     values ('life-goal-pictures', '11111111-1111-1111-1111-111111111111/aaaaaaaa-0000-0000-0000-000000000002.jpg',
             '11111111-1111-1111-1111-111111111111') $$,
  'the owner uploads into their own folder');
select throws_ok(
  $$ insert into storage.objects (bucket_id, name, owner_id)
     values ('life-goal-pictures', '22222222-2222-2222-2222-222222222222/planted.jpg',
             '11111111-1111-1111-1111-111111111111') $$,
  '42501', null,
  'but not into someone else''s');
select is(
  (select count(*)::integer from storage.objects where bucket_id = 'life-goal-pictures'),
  1,
  'and sees only their own files');
select is_empty(
  $$ delete from storage.objects where bucket_id = 'life-goal-pictures'
       and name like '22222222-2222-2222-2222-222222222222/%' returning name $$,
  'and can''t remove a stranger''s');

reset role;
select set_config('request.jwt.claims', '', true);
set local role anon;
select throws_ok(
  $$ select count(*) from public.life_goals $$,
  '42501', null,
  'nobody signed out reads life goals');

select * from finish();
rollback;

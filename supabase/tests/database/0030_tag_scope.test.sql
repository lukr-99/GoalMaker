-- Tags that belong to an area or a project (migration 0030, GM-107): one place at most, only the
-- owner's own area or project, and a stranger's tags stay out of sight.
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's area and a tag of theirs in it.
insert into public.areas (id, owner_id, name, color)
values ('99999999-3000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs', 'blue');
insert into public.tags (id, owner_id, name, area_id)
values ('99999999-3000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'secret',
        '99999999-3000-0000-0000-000000000001');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.areas (id, name, color) values ('aaaaaaaa-3000-0000-0000-000000000001', 'School', 'violet');
insert into public.projects (id, name) values ('bbbbbbbb-3000-0000-0000-000000000001', 'Thesis');

select lives_ok(
  $$ insert into public.tags (id, name) values ('cccccccc-3000-0000-0000-000000000001', 'everywhere') $$,
  'a tag may belong to nothing, and is then for everything');
select lives_ok(
  $$ insert into public.tags (id, name, area_id)
     values ('cccccccc-3000-0000-0000-000000000002', 'forms', 'aaaaaaaa-3000-0000-0000-000000000001') $$,
  'a tag may belong to one of the owner''s areas');
select lives_ok(
  $$ insert into public.tags (id, name, project_id)
     values ('cccccccc-3000-0000-0000-000000000003', 'chapter', 'bbbbbbbb-3000-0000-0000-000000000001') $$,
  'a tag may belong to one of the owner''s projects');
select throws_ok(
  $$ insert into public.tags (id, name, area_id, project_id)
     values ('cccccccc-3000-0000-0000-000000000004', 'both', 'aaaaaaaa-3000-0000-0000-000000000001',
             'bbbbbbbb-3000-0000-0000-000000000001') $$,
  '23514', null,
  'a tag belongs to an area or a project, never both');
select throws_ok(
  $$ insert into public.tags (id, name, area_id)
     values ('cccccccc-3000-0000-0000-000000000005', 'borrowed', '99999999-3000-0000-0000-000000000001') $$,
  '23503', null,
  'a tag cannot belong to a stranger''s area');
select is(
  (select count(*)::int from public.tags where id = '99999999-3000-0000-0000-000000000002'),
  0,
  'the owner never sees the stranger''s tags');
select results_eq(
  $$ select name from public.tags where area_id = 'aaaaaaaa-3000-0000-0000-000000000001' order by name $$,
  $$ values ('forms'::text) $$,
  'the owner reads which tags belong to an area');

select * from finish();
rollback;

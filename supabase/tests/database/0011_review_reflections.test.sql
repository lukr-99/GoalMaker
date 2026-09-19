-- The reflections a review keeps: the array, its length and the shape of each entry (migration 0011).
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select lives_ok(
  $$ insert into public.reviews (id, kind, period_start)
     values ('aaaaaaaa-0000-0000-0000-000000000001', 'weekly', '2026-09-14') $$,
  'a new review starts without reflections');
select is(
  (select reflections from public.reviews where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  '[]'::jsonb,
  'reflections start as an empty array');

select lives_ok(
  $$ update public.reviews
     set reflections = '[{"prompt": "wins/proud", "answer": "Shipped the habit screen."},
                         {"prompt": "energy/drained", "answer": ""}]'::jsonb
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  'the owner writes prompts and answers');

select throws_ok(
  $$ update public.reviews set reflections = '{"prompt": "wins/proud"}'::jsonb
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  null,
  null,
  'reflections must be an array');
select throws_ok(
  $$ update public.reviews set reflections = '[{"prompt": "wins/proud"}]'::jsonb
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  null,
  null,
  'an entry without an answer is refused');
select throws_ok(
  $$ update public.reviews set reflections = '[{"prompt": "", "answer": "x"}]'::jsonb
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  null,
  null,
  'an entry without a prompt id is refused');
select throws_ok(
  $$ update public.reviews
     set reflections = (select jsonb_agg(jsonb_build_object('prompt', 'wins/proud', 'answer', 'x')) from generate_series(1, 21))
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '23514',
  null,
  'a review holds at most twenty reflections');

select * from finish();
rollback;

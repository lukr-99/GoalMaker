-- The tombstone purge (migration 0005).
begin;
create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

insert into public.tasks (id, owner_id, title, deleted_at) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Old', now() - interval '91 days'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Recent', now() - interval '89 days');
insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Alive');
insert into public.task_steps (id, owner_id, task_id, title)
values ('cccccccc-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-0000-0000-0000-000000000003', 'Shoes');
update public.activity_log set created_at = now() - interval '100 days' where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001';

select isnt_empty(
  $$ select 1 from cron.job where jobname = 'goalmaker-purge-tombstones' and schedule = '17 3 * * *' $$,
  'the purge runs daily');

select is(public.purge_tombstones(), 2, 'one old tombstone and its old log entry are purged');
select is(
  (select array_agg(title order by title) from public.tasks),
  array['Alive', 'Recent'],
  'recent tombstones and live rows stay');
select is((select count(*)::int from public.task_steps), 1, 'live children stay');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
select throws_ok($$ select public.purge_tombstones() $$, '42501', null, 'clients can''t run the purge');
select throws_ok(
  $$ select public.purge_tombstones(interval '0 days') $$,
  '42501', null, 'clients can''t purge with their own cutoff either');
reset role;

select * from finish();
rollback;

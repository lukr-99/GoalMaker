-- Connector links and undo (migration 0007).
begin;
create extension if not exists pgtap with schema extensions;
select plan(25);

insert into auth.users (id, instance_id, aud, role, email)
values
  ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'owner@example.test'),
  ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'stranger@example.test');

-- The stranger's link and a change of theirs, made as the test runner.
insert into public.connector_links (owner_id, secret_hash)
values ('22222222-2222-2222-2222-222222222222', repeat('a', 64));
insert into public.tasks (id, owner_id, title)
values ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Theirs');

create temporary table secrets (n int, secret text) on commit drop;
grant all on secrets to authenticated;

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

-- Links
insert into secrets values (1, public.create_connector_link());
select matches((select secret from secrets where n = 1), '^[A-Za-z0-9_-]{43}$', 'a secret is 43 URL-safe characters');
select is((select count(*)::int from public.connector_links), 1, 'the owner sees only their own link');
select throws_ok(
  $$ select secret_hash from public.connector_links $$,
  '42501', null,
  'the owner can''t read the hash');
select throws_ok(
  $$ insert into public.connector_links (owner_id, secret_hash) values ('11111111-1111-1111-1111-111111111111', repeat('b', 64)) $$,
  '42501', null,
  'links are made only through create_connector_link');
insert into secrets values (2, public.create_connector_link());
select is((select count(*)::int from public.connector_links where revoked_at is null), 1, 'creating again rotates: one active link');
select is((select count(*)::int from public.connector_links), 2, 'the rotated link stays listed as revoked');
select throws_ok(
  $$ select * from public.connector_resolve(repeat('0', 64)) $$,
  '42501', null,
  'the owner can''t resolve links');

reset role;
select is(
  (select secret_hash from public.connector_links where owner_id = '11111111-1111-1111-1111-111111111111' and revoked_at is null),
  encode(extensions.digest((select secret from secrets where n = 2), 'sha256'), 'hex'),
  'only the SHA-256 hash of the secret is stored');
select is(
  (select owner_id from public.connector_resolve(encode(extensions.digest((select secret from secrets where n = 2), 'sha256'), 'hex'))),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'the function resolves the active link to its owner');
select is(
  (select count(*)::int from public.connector_resolve(encode(extensions.digest((select secret from secrets where n = 1), 'sha256'), 'hex'))),
  0,
  'a rotated link resolves to nothing');
select isnt(
  (select last_used_at from public.connector_links where owner_id = '11111111-1111-1111-1111-111111111111' and revoked_at is null),
  null,
  'resolving marks the link used');
select is(
  (select allowed from public.connector_resolve(encode(extensions.digest((select secret from secrets where n = 2), 'sha256'), 'hex'), 1)),
  false,
  'the call over the limit in the same minute is refused');
update public.connector_links set window_started_at = now() - interval '61 seconds'
where owner_id = '11111111-1111-1111-1111-111111111111' and revoked_at is null;
select is(
  (select allowed from public.connector_resolve(encode(extensions.digest((select secret from secrets where n = 2), 'sha256'), 'hex'), 2)),
  true,
  'a new minute starts a new window');

set local role authenticated;
select is(public.revoke_connector_links(), 1, 'revoking kills the active link');
reset role;
select is(
  (select count(*)::int from public.connector_links where owner_id = '22222222-2222-2222-2222-222222222222' and revoked_at is null),
  1,
  'the stranger''s link is untouched');
select is(
  (select count(*)::int from public.connector_resolve(encode(extensions.digest((select secret from secrets where n = 2), 'sha256'), 'hex'))),
  0,
  'a revoked link resolves to nothing');

set local role anon;
select throws_ok($$ select public.create_connector_link() $$, '42501', null, 'anonymous callers can''t make links');

-- Undo
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);
insert into public.tasks (id, title) values ('aaaaaaaa-0000-0000-0000-000000000001', 'Call the bank');
update public.tasks set title = 'Call the bank at 9' where id = 'aaaaaaaa-0000-0000-0000-000000000001';

select lives_ok(
  $$ select public.undo_activity((select max(id) from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001')) $$,
  'the owner undoes an edit');
select is((select title from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'), 'Call the bank', 'the edit is gone');
select throws_ok(
  $$ select public.undo_activity((select max(id) from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001' and undone_at is not null)) $$,
  '55000', null,
  'an undone change can''t be undone twice');
update public.tasks set title = 'Call the bank today' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
select throws_ok(
  $$ select public.undo_activity((select min(id) from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001')) $$,
  '40001', null,
  'a change the row has moved on from is refused');

update public.tasks set deleted_at = now() where id = 'aaaaaaaa-0000-0000-0000-000000000001';
select public.undo_activity((select max(id) from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001'));
select is((select deleted_at from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000001'), null, 'undoing a delete brings the task back');

insert into public.tasks (id, title) values ('aaaaaaaa-0000-0000-0000-000000000002', 'Oops');
select public.undo_activity((select max(id) from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000002'));
select isnt((select deleted_at from public.tasks where id = 'aaaaaaaa-0000-0000-0000-000000000002'), null, 'undoing a create deletes the task softly');

reset role;
insert into secrets
select 3, max(id)::text from public.activity_log where entity_id = '99999999-0000-0000-0000-000000000001';
set local role authenticated;
select throws_ok(
  format('select public.undo_activity(%s)', (select secret from secrets where n = 3)),
  'P0002', null,
  'someone else''s change is not found');

reset role;
select is(
  (select count(*)::int from public.activity_log where entity_id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  6,
  'every undo is itself in the log');

select * from finish();
rollback;

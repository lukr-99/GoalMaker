-- The update channel bucket (migration 0002).
begin;
create extension if not exists pgtap with schema extensions;
select plan(5);

select is(
  (select public from storage.buckets where id = 'releases'),
  false,
  'the releases bucket is private');

select is(
  (select file_size_limit from storage.buckets where id = 'releases'),
  52428800::bigint,
  'the releases bucket caps files at 50 MB');

insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

insert into storage.objects (bucket_id, name) values ('releases', 'latest/manifest.json');
insert into storage.objects (bucket_id, name) values ('releases', 'latest/manifest.sig');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

select is(
  (select count(*)::int from storage.objects where bucket_id = 'releases'),
  2,
  'signed-in users can see release files');

select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('releases', 'latest/evil.apk') $$,
  '42501', null, 'signed-in users cannot upload release files');

reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role": "anon"}', true);

select is(
  (select count(*)::int from storage.objects where bucket_id = 'releases'),
  0,
  'anonymous callers see no release files');

reset role;
select * from finish();
rollback;

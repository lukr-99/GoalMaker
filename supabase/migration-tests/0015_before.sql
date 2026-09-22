-- State before 0015: one task the owner made in an app and one Claude made through the connector,
-- each with the activity log's record of who created it.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-1500-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Mine');

select set_config('request.headers', '{"x-goalmaker-actor": "claude"}', true);
insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-1500-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'From Claude');
select set_config('request.headers', '{}', true);

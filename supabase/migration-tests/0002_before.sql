-- State before 0002: a user with a customized profile.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
update public.profiles
set display_name = 'Owner', day_rollover_hour = 5
where id = '11111111-1111-1111-1111-111111111111';

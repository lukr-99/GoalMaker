-- State before 0004: a user with an area and a task, written before the activity log existed.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.areas (id, owner_id, name, color)
values ('bbbbbbbb-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Health', 'coral');
insert into public.tasks (id, owner_id, title, area_id)
values ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Run',
        'bbbbbbbb-0000-0000-0000-000000000001');

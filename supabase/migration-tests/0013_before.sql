-- State before 0013: a user with an area and a task, from before projects existed.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.areas (id, owner_id, name, color)
values ('aaaaaaaa-1111-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Code', 'violet');
insert into public.tasks (id, owner_id, title, area_id)
values ('cccccccc-1111-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Fix the sync',
        'aaaaaaaa-1111-0000-0000-000000000001');

-- State before 0026: an owner with an area and a task, the things an event can sit in or beside.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.areas (id, owner_id, name, color)
values ('aaaaaaaa-2600-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Personal', 'violet');
insert into public.tasks (id, owner_id, title, made_by)
values ('aaaaaaaa-2600-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Pack for the trip', 'owner');

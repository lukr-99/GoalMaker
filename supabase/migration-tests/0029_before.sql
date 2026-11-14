-- State before 0029: an owner and one of their tasks. Tasks have no Focus Board position yet.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-2900-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Plan the week');

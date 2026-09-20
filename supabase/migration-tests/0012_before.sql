-- State before 0012: a planned task from before anything counted how often it was moved.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.tasks (id, owner_id, title, planned_date)
values ('cccccccc-0012-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Call the dentist',
        '2026-09-18');

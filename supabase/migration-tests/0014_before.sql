-- State before 0014: a habit from when every habit counted up to something.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.habits (id, owner_id, name, cadence, measure, target, unit, starts_on)
values ('dddddddd-1111-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Water',
        'daily', 'count', 8, 'glasses', '2026-09-01');

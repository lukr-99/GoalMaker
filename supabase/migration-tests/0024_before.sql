-- State before 0024: a daily limit and a weekly habit to build, which stay as they are.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.habits (id, owner_id, name, cadence, measure, target, direction, starts_on)
values ('dddddddd-2400-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Snacks',
        'daily', 'count', 2, 'at_most', '2026-09-01');
insert into public.habits (id, owner_id, name, cadence, times, measure, starts_on)
values ('dddddddd-2400-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Gym',
        'per_week', 3, 'check', '2026-09-01');

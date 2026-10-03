-- State before 0022: a habit from when no habit reminded.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.habits (id, owner_id, name, cadence, measure, starts_on)
values ('dddddddd-2200-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Read',
        'daily', 'check', '2026-09-01');

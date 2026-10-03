-- State before 0021: a habit with a check-in from when nothing could fail.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.habits (id, owner_id, name, cadence, measure, starts_on)
values ('dddddddd-2100-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Read',
        'daily', 'check', '2026-09-01');
insert into public.habit_checkins (id, owner_id, habit_id, day, value)
values ('eeeeeeee-2100-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
        'dddddddd-2100-0000-0000-000000000001', '2026-10-01', 1);

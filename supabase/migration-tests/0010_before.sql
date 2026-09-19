-- State before 0010: a user with a numeric goal, from before habits existed.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.goals (id, owner_id, title, horizon, period_start, progress_mode, target, unit)
values ('eeeeeeee-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Run 80 km', 'month', '2026-09-01', 'number', 80, 'km');

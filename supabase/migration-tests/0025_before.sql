-- State before 0025: a want cooling, which stays a want.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.wants (id, owner_id, title, reason, price, cooldown_days, added_on, cools_until, made_by)
values ('aaaaaaaa-2500-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Trail shoes',
        'The old ones have holes', 3400, 30, '2026-10-05', '2026-11-04', 'owner');

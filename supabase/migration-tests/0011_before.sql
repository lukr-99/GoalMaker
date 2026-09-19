-- State before 0011: a review written when reviews had no reflections.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');
insert into public.reviews (id, owner_id, kind, period_start, mood, energy, summary)
values ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
        'weekly', '2026-09-14', 4, 3, 'A good week.');

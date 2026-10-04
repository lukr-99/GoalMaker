-- State before 0023: an owner with an area and a want, the things a life goal can sit in or beside.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.areas (id, owner_id, name, color)
values ('aaaaaaaa-2100-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Personal', 'violet');

insert into public.wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until)
values ('aaaaaaaa-2100-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Driving gloves',
        'For the R8 one day', 30, '2026-10-04', '2026-11-03');

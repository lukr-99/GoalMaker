-- State before 0017: an owner with a project and a want, the things Tally sits beside.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.projects (id, owner_id, name, local_folder)
values ('aaaaaaaa-1700-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker',
        'C:\Code\GoalMaker');

insert into public.wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until)
values ('aaaaaaaa-1700-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Before Tally',
        'Still wanted', 30, '2026-09-28', '2026-10-28');

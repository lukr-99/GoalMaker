-- State before 0016: an owner with an area and a task, the things a want can point at or sit beside.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.areas (id, owner_id, name, color)
values ('aaaaaaaa-1600-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Personal', 'violet');

insert into public.tasks (id, owner_id, title)
values ('aaaaaaaa-1600-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Before wants');

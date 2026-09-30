-- State before 0018: a project with a done item and an open one.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

select set_config('request.jwt.claims',
  '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}', true);

insert into public.projects (id, owner_id, name)
values ('aaaaaaaa-1800-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker');

insert into public.tasks (id, owner_id, title, status, completed_at, project_id, board_column)
values ('aaaaaaaa-1800-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Shipped', 'done', now(),
        'aaaaaaaa-1800-0000-0000-000000000001', 'done'),
       ('aaaaaaaa-1800-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Still open', 'open', null,
        'aaaaaaaa-1800-0000-0000-000000000001', 'todo');

-- State before 0027: two projects with items made out of order (one deleted, two made at the same
-- moment), an item that left its project, and a plain task.
insert into auth.users (id, instance_id, aud, role, email)
values ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', 'owner@example.test');

insert into public.projects (id, owner_id, name)
values ('aaaaaaaa-2700-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker'),
       ('aaaaaaaa-2700-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Relay');

insert into public.tasks (id, owner_id, title, project_id, board_column, created_at, deleted_at)
values
  ('cccccccc-2700-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Third',
   'aaaaaaaa-2700-0000-0000-000000000001', 'todo', '2026-09-03 10:00+00', null),
  ('cccccccc-2700-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'First',
   'aaaaaaaa-2700-0000-0000-000000000001', 'todo', '2026-09-01 10:00+00', null),
  ('cccccccc-2700-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Second, deleted',
   'aaaaaaaa-2700-0000-0000-000000000001', 'done', '2026-09-02 10:00+00', '2026-09-05 10:00+00'),
  ('cccccccc-2700-0000-0000-000000000012', '11111111-1111-1111-1111-111111111111', 'Relay b',
   'aaaaaaaa-2700-0000-0000-000000000002', 'backlog', '2026-09-01 10:00+00', null),
  ('cccccccc-2700-0000-0000-000000000011', '11111111-1111-1111-1111-111111111111', 'Relay a',
   'aaaaaaaa-2700-0000-0000-000000000002', 'backlog', '2026-09-01 10:00+00', null),
  ('cccccccc-2700-0000-0000-000000000021', '11111111-1111-1111-1111-111111111111', 'Plain',
   null, null, '2026-08-01 10:00+00', null);

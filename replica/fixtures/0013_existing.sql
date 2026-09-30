-- State before 0013: a project with a done item, which keeps its place on the board.
INSERT INTO projects (id, owner_id, name, created_at, updated_at)
VALUES ('aaaaaaaa-1300-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker',
        '2026-09-30T08:00:00.000000Z', '2026-09-30T08:00:00.000000Z');
INSERT INTO tasks (id, owner_id, title, status, completed_at, project_id, board_column, created_at, updated_at)
VALUES ('aaaaaaaa-1300-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Shipped', 'done',
        '2026-09-30T09:00:00.000000Z', 'aaaaaaaa-1300-0000-0000-000000000001', 'done',
        '2026-09-30T08:00:00.000000Z', '2026-09-30T09:00:00.000000Z');

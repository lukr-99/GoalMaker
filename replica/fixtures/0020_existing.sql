-- State before 0020: a synced project and one of its items, which get no key and no number until the
-- server sends them.
INSERT INTO projects (id, owner_id, name, created_at, updated_at)
VALUES ('aaaaaaaa-2000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker',
        '2026-10-06T08:00:00.000000Z', '2026-10-06T08:00:00.000000Z');
INSERT INTO tasks (id, owner_id, title, status, project_id, board_column, created_at, updated_at)
VALUES ('aaaaaaaa-2000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Item ids', 'open',
        'aaaaaaaa-2000-0000-0000-000000000001', 'todo', '2026-10-06T08:00:00.000000Z', '2026-10-06T08:00:00.000000Z');

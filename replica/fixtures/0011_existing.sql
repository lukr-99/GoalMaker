-- State before 0011: a synced area and task, which wants may point at and sit beside.
INSERT INTO areas (id, owner_id, name, color, created_at, updated_at)
VALUES ('aaaaaaaa-1100-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Personal', 'violet',
        '2026-09-20T08:00:00.000000Z', '2026-09-20T08:00:00.000000Z');
INSERT INTO tasks (id, owner_id, title, created_at, updated_at)
VALUES ('aaaaaaaa-1100-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'From before',
        '2026-09-20T08:00:00.000000Z', '2026-09-20T08:00:00.000000Z');

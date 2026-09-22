-- State before 0010: a synced task, another synced table, and the watermarks both left behind.
INSERT INTO tasks (id, owner_id, title, created_at, updated_at)
VALUES ('aaaaaaaa-1000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'From before',
        '2026-09-20T08:00:00.000000Z', '2026-09-20T08:00:00.000000Z');
INSERT INTO sync_state (entity, watermark, last_pulled_at)
VALUES ('tasks', '2026-09-21T08:00:00.000000Z', '2026-09-21T08:00:05.000000Z'),
       ('areas', '2026-09-21T07:00:00.000000Z', '2026-09-21T08:00:05.000000Z');

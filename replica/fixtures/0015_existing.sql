-- State before 0015: a synced area and want, which life goals may sit in and beside.
INSERT INTO areas (id, owner_id, name, color, created_at, updated_at)
VALUES ('aaaaaaaa-1500-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Personal', 'violet',
        '2026-10-04T08:00:00.000000Z', '2026-10-04T08:00:00.000000Z');
INSERT INTO wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until, created_at, updated_at)
VALUES ('aaaaaaaa-1500-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Driving gloves',
        'For the R8 one day', 30, '2026-10-04', '2026-11-03',
        '2026-10-04T08:00:00.000000Z', '2026-10-04T08:00:00.000000Z');

-- State before 0012: a synced project and a want, which Tally's days and rules sit beside.
INSERT INTO projects (id, owner_id, name, created_at, updated_at)
VALUES ('aaaaaaaa-1200-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GoalMaker',
        '2026-09-28T08:00:00.000000Z', '2026-09-28T08:00:00.000000Z');
INSERT INTO wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until, created_at, updated_at)
VALUES ('aaaaaaaa-1200-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Before Tally',
        'Still wanted', 30, '2026-09-28', '2026-10-28', '2026-09-28T08:00:00.000000Z', '2026-09-28T08:00:00.000000Z');

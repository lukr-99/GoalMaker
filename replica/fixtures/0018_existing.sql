-- State before 0018: a synced want, which stays a want.
INSERT INTO wants (id, owner_id, title, reason, cooldown_days, added_on, cools_until, created_at, updated_at)
VALUES ('aaaaaaaa-1800-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Trail shoes',
        'The old ones have holes', 30, '2026-10-05', '2026-11-04',
        '2026-10-05T08:00:00.000000Z', '2026-10-05T08:00:00.000000Z');

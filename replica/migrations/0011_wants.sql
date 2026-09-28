-- 0011: wants and their cooldown thresholds (Supabase migration 0016, docs/wants.md).
--
-- The columns are laxer than the server's, like the rest of the replica: the server checks the
-- reason, the currency and that a want cools exactly its days after it was added.

CREATE TABLE wants (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    title TEXT NOT NULL,
    reason TEXT NOT NULL,
    link TEXT,
    price REAL,
    currency TEXT NOT NULL DEFAULT 'CZK',
    area_id TEXT,
    cooldown_days INTEGER NOT NULL,
    added_on TEXT NOT NULL,
    cools_until TEXT NOT NULL,
    decision TEXT CHECK (decision IS NULL OR decision IN ('bought', 'dropped')),
    decided_at TEXT,
    decision_note TEXT NOT NULL DEFAULT '',
    checked_price REAL,
    checked_at TEXT,
    checked_note TEXT NOT NULL DEFAULT '',
    made_by TEXT DEFAULT 'owner' CHECK (made_by IS NULL OR made_by IN ('owner', 'claude')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX wants_state_idx ON wants (decision, cools_until);

CREATE TABLE want_cooldowns (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    small_under REAL NOT NULL DEFAULT 1000,
    small_days INTEGER NOT NULL DEFAULT 7,
    medium_under REAL NOT NULL DEFAULT 10000,
    medium_days INTEGER NOT NULL DEFAULT 30,
    large_days INTEGER NOT NULL DEFAULT 90,
    unpriced_days INTEGER NOT NULL DEFAULT 30,
    currency TEXT NOT NULL DEFAULT 'CZK',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

-- 0012: Tally's days, rules and categories (Supabase migration 0017, docs/tally.md, ADR 0013).
--
-- The columns are laxer than the server's, like the rest of the replica: the server checks the
-- minutes, the kinds and that a phone's time never names a project. No column holds an app or a
-- window's name; the raw record stays in each device's own store and never enters the replica.

CREATE TABLE tally_categories (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name TEXT NOT NULL,
    color TEXT NOT NULL DEFAULT 'violet',
    emoji TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE tally_rules (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    match TEXT NOT NULL CHECK (match IN ('app', 'title', 'folder')),
    pattern TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'any' CHECK (platform IN ('android', 'windows', 'any')),
    category TEXT NOT NULL,
    project_id TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE tally_days (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    day TEXT NOT NULL,
    device TEXT NOT NULL,
    device_kind TEXT NOT NULL CHECK (device_kind IN ('phone', 'pc')),
    category TEXT NOT NULL,
    project_id TEXT,
    minutes INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX tally_days_day_idx ON tally_days (day);

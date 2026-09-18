-- 0001: the device replica (ADR 0007, docs/sync.md), used as-is by the Android and Windows apps.
--
-- The synced tables mirror the Supabase columns. SQLite has no uuid, date or timestamptz types, so
-- ids are TEXT, dates are TEXT 'YYYY-MM-DD', times 'HH:MM:SS', and timestamps TEXT in UTC as
-- 'YYYY-MM-DDTHH:MM:SS.ffffffZ' so that text order is time order. Booleans are INTEGER 0/1.
--
-- There are no foreign keys between synced tables: the server enforces integrity, and a replica
-- briefly holds rows whose parent arrives in a later page or table of the same pull.

CREATE TABLE areas (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name TEXT NOT NULL,
    color TEXT NOT NULL DEFAULT 'violet',
    emoji TEXT,
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE tags (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE tasks (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    title TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    planned_date TEXT,
    planned_time TEXT,
    deadline TEXT,
    top_priority INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done', 'dropped')),
    completed_at TEXT,
    area_id TEXT,
    recurrence TEXT,
    series_id TEXT,
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX tasks_open_by_day ON tasks (status, planned_date) WHERE deleted_at IS NULL;

CREATE TABLE task_steps (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    task_id TEXT NOT NULL,
    title TEXT NOT NULL,
    done INTEGER NOT NULL DEFAULT 0,
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX task_steps_by_task ON task_steps (task_id);

CREATE TABLE task_tags (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    task_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX task_tags_by_task ON task_tags (task_id);

CREATE TABLE reminders (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    task_id TEXT NOT NULL,
    fire_at TEXT,
    offset_minutes INTEGER,
    important INTEGER NOT NULL DEFAULT 0,
    state TEXT NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'snoozed', 'dismissed', 'done')),
    snoozed_until TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX reminders_by_task ON reminders (task_id);

-- Local changes waiting to be pushed, in order. payload is the full row as JSON.
CREATE TABLE outbox (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    entity TEXT NOT NULL,
    row_id TEXT NOT NULL,
    payload TEXT NOT NULL,
    queued_at TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX outbox_by_row ON outbox (entity, row_id);

-- Per table: the largest server updated_at pulled so far.
CREATE TABLE sync_state (
    entity TEXT NOT NULL PRIMARY KEY,
    watermark TEXT,
    last_pulled_at TEXT
);

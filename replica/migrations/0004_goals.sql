-- 0004: goals and their entries (Supabase migration 0009, docs/goals.md).
--
-- A goal belongs to a year, month, week or day, named by the period's first day, and may serve a
-- parent goal of a longer period. Entries are amounts logged on a numeric goal. A task can serve a
-- goal through goal_id.

CREATE TABLE goals (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    title TEXT NOT NULL,
    emoji TEXT,
    horizon TEXT NOT NULL CHECK (horizon IN ('year', 'month', 'week', 'day')),
    period_start TEXT NOT NULL,
    parent_id TEXT,
    progress_mode TEXT NOT NULL DEFAULT 'done' CHECK (progress_mode IN ('done', 'tasks', 'number')),
    target REAL,
    unit TEXT,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done', 'dropped')),
    completed_at TEXT,
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX goals_by_period ON goals (horizon, period_start);

CREATE TABLE goal_entries (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    goal_id TEXT NOT NULL,
    day TEXT NOT NULL,
    amount REAL NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX goal_entries_by_goal ON goal_entries (goal_id);

ALTER TABLE tasks ADD COLUMN goal_id TEXT;

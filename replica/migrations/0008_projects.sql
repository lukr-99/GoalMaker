-- 0008: projects and their boards (Supabase migration 0013, docs/projects.md).
--
-- A project item is an ordinary task with project columns on it, so nothing about tasks changes for
-- work outside a project. The columns are nullable with defaults, like the rest of the replica: a row
-- that arrives without them (an older device, a partial write) is a task with no project.

CREATE TABLE projects (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    area_id TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'paused', 'done')),
    repository_url TEXT,
    local_folder TEXT,
    notes TEXT NOT NULL DEFAULT '',
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE project_milestones (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    project_id TEXT NOT NULL,
    name TEXT NOT NULL,
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX project_milestones_project_idx ON project_milestones (project_id, position);

ALTER TABLE tasks ADD COLUMN project_id TEXT;
ALTER TABLE tasks ADD COLUMN item_type TEXT DEFAULT 'task' CHECK (item_type IS NULL OR item_type IN ('task', 'idea', 'bug'));
ALTER TABLE tasks ADD COLUMN board_column TEXT CHECK (board_column IS NULL OR board_column IN ('backlog', 'todo', 'doing', 'done'));
ALTER TABLE tasks ADD COLUMN priority TEXT DEFAULT 'normal' CHECK (priority IS NULL OR priority IN ('low', 'normal', 'high', 'urgent'));
ALTER TABLE tasks ADD COLUMN milestone_id TEXT;

CREATE INDEX tasks_project_idx ON tasks (project_id, board_column, position);

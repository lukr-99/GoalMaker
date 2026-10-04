-- 0015: life goals and their pictures (Supabase migration 0021, docs/life-goals.md).
--
-- The columns are laxer than the server's, like the rest of the replica: the server checks the why,
-- the status with its time and a picture's size. The picture files are not here (ADR 0018).

CREATE TABLE life_goals (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    title TEXT NOT NULL,
    why TEXT NOT NULL,
    by_date TEXT,
    area_id TEXT,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'achieved', 'dropped')),
    closed_at TEXT,
    position REAL NOT NULL DEFAULT 0,
    made_by TEXT DEFAULT 'owner' CHECK (made_by IS NULL OR made_by IN ('owner', 'claude')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE life_goal_pictures (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    life_goal_id TEXT NOT NULL,
    position REAL NOT NULL DEFAULT 0,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX life_goal_pictures_goal_idx ON life_goal_pictures (life_goal_id);

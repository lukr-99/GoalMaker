-- 0019: calendar events (Supabase migration 0026, docs/calendar.md).
--
-- The columns are laxer than the server's, like the rest of the replica: the server checks the title
-- and notes lengths, that the last day is not before the first and at most 366 days after it.

CREATE TABLE events (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    title TEXT NOT NULL,
    starts_on TEXT NOT NULL,
    ends_on TEXT NOT NULL,
    notes TEXT,
    area_id TEXT,
    made_by TEXT DEFAULT 'owner' CHECK (made_by IS NULL OR made_by IN ('owner', 'claude')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX events_owner_start_idx ON events (owner_id, starts_on);

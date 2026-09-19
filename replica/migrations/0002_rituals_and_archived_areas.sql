-- 0002: archived areas and ritual runs (Supabase migration 0006, docs/reminders.md).
--
-- An archived area keeps its row with archived_at set. A ritual run records that a ritual was done
-- or skipped for a planning day; its id comes from the owner, the ritual and the day, so both
-- devices make the same row.

ALTER TABLE areas ADD COLUMN archived_at TEXT;

CREATE TABLE ritual_runs (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    ritual TEXT NOT NULL CHECK (ritual IN ('plan_tomorrow', 'weekly_review', 'monthly_review')),
    day TEXT NOT NULL,
    outcome TEXT NOT NULL DEFAULT 'done' CHECK (outcome IN ('done', 'skipped')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX ritual_runs_by_day ON ritual_runs (ritual, day);

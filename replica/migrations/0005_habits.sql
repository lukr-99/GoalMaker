-- 0005: habits, their check-ins and pauses (Supabase migration 0010, docs/habits.md).
--
-- A habit runs daily, on weekdays (a bitmask, Monday 1 to Sunday 64), or N times a week or a month,
-- and is measured as a check, a count or an amount. A day has one check-in per habit, named by habit
-- and day; pauses are ranges of days that neither break a streak nor count.

CREATE TABLE habits (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name TEXT NOT NULL,
    emoji TEXT,
    cadence TEXT NOT NULL DEFAULT 'daily' CHECK (cadence IN ('daily', 'weekdays', 'per_week', 'per_month')),
    weekdays INTEGER,
    times INTEGER,
    measure TEXT NOT NULL DEFAULT 'check' CHECK (measure IN ('check', 'count', 'amount')),
    target REAL,
    unit TEXT,
    goal_id TEXT,
    starts_on TEXT NOT NULL,
    archived_at TEXT,
    position REAL NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE TABLE habit_checkins (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    habit_id TEXT NOT NULL,
    day TEXT NOT NULL,
    value REAL NOT NULL DEFAULT 0,
    skipped INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX habit_checkins_by_habit ON habit_checkins (habit_id, day);

CREATE TABLE habit_pauses (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    habit_id TEXT NOT NULL,
    starts_on TEXT NOT NULL,
    ends_on TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX habit_pauses_by_habit ON habit_pauses (habit_id);

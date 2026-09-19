-- 0003: reviews (Supabase migration 0008, docs/connector.md).
--
-- One review per kind and period; its id comes from the owner, the kind and the period start
-- (contracts/vectors/reviews.json), so every device and the connector make the same row.

CREATE TABLE reviews (
    id TEXT NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('weekly', 'monthly', 'yearly')),
    period_start TEXT NOT NULL,
    mood INTEGER CHECK (mood BETWEEN 1 AND 5),
    energy INTEGER CHECK (energy BETWEEN 1 AND 5),
    summary TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
);

CREATE INDEX reviews_by_period ON reviews (kind, period_start);

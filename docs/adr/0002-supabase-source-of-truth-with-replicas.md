# ADR 0002: Supabase is the source of truth; devices keep replicas

Both apps must work offline, and reminders must fire even when sync is behind, but the owner wants
Supabase to be "the main thing". Supabase Postgres is authoritative. Each device keeps a full replica
(Room on Android, SQLite on Windows) and an outbox of pending changes, pushes idempotent upserts
keyed by device-made UUIDs, and pulls by server `updated_at` watermarks. Conflicts resolve as last
writer wins per row by the server timestamp; deletes are tombstones kept 90 days, after which a stale
device does a full resync. Per-row last-writer-wins can lose a concurrent edit to another field of
the same row; for a single user on two devices that is rare and accepted, and the merge rule is a
shared contract so both apps behave the same.

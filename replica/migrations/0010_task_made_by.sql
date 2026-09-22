-- 0010: who made a task (Supabase migration 0015, docs/projects.md).
--
-- 'owner' or 'claude', set once when the task is made. The column is nullable with a default, like
-- the rest of the replica: a row that arrives without it (an older backup, an older device) is the
-- owner's, which is what the server says of such a row too.
--
-- The rows already here can't know who made them, so tasks sync again from scratch: forgetting the
-- watermark makes the next sync replace them with the server's rows, which say (docs/sync.md).

ALTER TABLE tasks ADD COLUMN made_by TEXT DEFAULT 'owner'
    CHECK (made_by IS NULL OR made_by IN ('owner', 'claude'));

UPDATE sync_state SET watermark = NULL WHERE entity = 'tasks';

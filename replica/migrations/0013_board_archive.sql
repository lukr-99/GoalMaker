-- 0013: done items leave the board (Supabase migration 0018, docs/projects.md). A project keeps done
-- items archive_after_days after they were finished (null: until archived by hand), and
-- board_archived_at marks an item archived by hand. The server clears it when an item is reopened.

ALTER TABLE projects ADD COLUMN archive_after_days INTEGER DEFAULT 14;

ALTER TABLE tasks ADD COLUMN board_archived_at TEXT;

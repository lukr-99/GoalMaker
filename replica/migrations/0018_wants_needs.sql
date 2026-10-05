-- 0018: needs beside wants (Supabase migration 0025, docs/wants.md).
--
-- A row from an older device is a want. The server checks that only a need has a day it is needed
-- by, that a need skips the cooldown, and that a want says why.

ALTER TABLE wants ADD COLUMN kind TEXT NOT NULL DEFAULT 'want' CHECK (kind IN ('want', 'need'));
ALTER TABLE wants ADD COLUMN need_by TEXT;

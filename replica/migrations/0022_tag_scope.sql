-- 0022: a tag can belong to one area or one project (Supabase migration 0030). Null for both is a tag
-- for everything.

ALTER TABLE tags ADD COLUMN area_id TEXT;
ALTER TABLE tags ADD COLUMN project_id TEXT;

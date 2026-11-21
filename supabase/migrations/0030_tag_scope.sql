-- 0030: a tag can belong to one area or one project (GM-107, docs/lists.md "Tags"). A tag with
-- neither is for everything, which every tag was before. Filtering by an area offers that area's tags
-- and the ones for everything; a project's board offers the project's, its area's and those.
--
-- A deleted area or project is soft-deleted, and the apps then make its tags for everything again; the
-- foreign keys' set null covers a row that is really removed.

alter table public.tags
  add column area_id uuid,
  add column project_id uuid;

alter table public.tags
  add constraint tags_area_fkey foreign key (area_id, owner_id)
    references public.areas (id, owner_id) on delete set null (area_id),
  add constraint tags_project_fkey foreign key (project_id, owner_id)
    references public.projects (id, owner_id) on delete set null (project_id),
  add constraint tags_one_place_check check (area_id is null or project_id is null);

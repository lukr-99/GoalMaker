/**
 * Tags that belong to an area or a project (docs/lists.md, "Tags"): which ones a filter offers, and how
 * Settings groups them. A tag whose area or project is gone counts as for everything. The apps' TagRules
 * do the same (contracts/vectors/tags.json).
 */
export interface PlacedTag {
  id: string;
  name: string;
  areaId?: string | null;
  projectId?: string | null;
}

/** Where a tag belongs, "area:<id>" or "project:<id>", or null when it is for everything (or its place is gone). */
export function placeOf(tag: PlacedTag, areaIds: Set<string>, projectIds: Set<string>): string | null {
  if (tag.areaId && areaIds.has(tag.areaId)) return `area:${tag.areaId}`;
  if (tag.projectId && projectIds.has(tag.projectId)) return `project:${tag.projectId}`;
  return null;
}

/**
 * The tags a tag picker offers, in their order: every tag with nothing chosen; with `areaId`, the tags for
 * everything and that area's; on a project's board (`projectId`), the tags for everything, that project's
 * and its area's. `projectAreas` maps each live project to its area.
 */
export function offeredTags<T extends PlacedTag>(
  tags: T[],
  areaIds: Set<string>,
  projectAreas: Map<string, string | null>,
  chosen: { areaId?: string | null; projectId?: string | null },
): T[] {
  if (!chosen.areaId && !chosen.projectId) return tags;
  const area = chosen.areaId ?? (chosen.projectId ? projectAreas.get(chosen.projectId) ?? null : null);
  const projectIds = new Set(projectAreas.keys());
  return tags.filter((tag) => {
    const place = placeOf(tag, areaIds, projectIds);
    return place === null || place === `area:${area}` || place === `project:${chosen.projectId}`;
  });
}

/** Settings' groups: the tags for everything, then each area's in order, then each project's; empty ones left out. */
export function tagGroups<T extends PlacedTag>(
  tags: T[],
  areaIds: string[],
  projectIds: string[],
): { place: string | null; tags: T[] }[] {
  const areas = new Set(areaIds);
  const projects = new Set(projectIds);
  const places = [null, ...areaIds.map((id) => `area:${id}`), ...projectIds.map((id) => `project:${id}`)];
  return places
    .map((place) => ({ place, tags: tags.filter((tag) => placeOf(tag, areas, projects) === place) }))
    .filter((group) => group.tags.length > 0);
}

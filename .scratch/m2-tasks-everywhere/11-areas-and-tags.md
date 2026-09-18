# M2-11: Areas and tags

**Status:** done 2026-09-19, except archiving and custom colors · **Milestone:** M2

## Scope
- Create, rename, recolor (palette or custom), add an emoji, reorder and archive areas; tags
  created from the composer or a manager; chips use the area palette from the design tokens.
- Filter any list by area and tag (spec, story 9); on Windows the sidebar lists areas and tags as
  filters.

## Acceptance criteria
- An area's color and emoji show consistently on both apps in every theme and mode.
- Filtering by an area or a tag works on every list and survives switching lists.

## Result
- Filtering (docs/lists.md): any list can be narrowed to an area, a tag or both before the list rules
  run; the `filter` cases in `contracts/vectors/lists.json` pin it and both apps pass them. The
  filter stays across Today, Tomorrow and the Inbox and falls away when its area or tag is deleted.
  Android shows filter chips above the lists; Windows lists areas and tags as filters in the sidebar
  and says what is shown under the list's title.
- Areas are added, renamed, recolored from the palette, given an emoji, reordered and deleted (their
  tasks stay and lose the area); tags are added, renamed and deleted with their links. A taken or
  blank name is refused and explained. Android: Settings, Areas and tags; Windows: a sidebar page
  and `--open areas`.
- Chips already used the area palette from the design tokens (M2-05).

## Left
- **Archiving areas** and **custom colors** need a migration: `areas` has no archive column, and
  `areas.color` only accepts palette ids (`^[a-z][a-z0-9-]{0,23}$`). Custom colors also need a rule
  for staying readable in every theme and mode. Both wait for Docker so the migration harness runs.
- Windows draws emoji in one color; WPF has no color emoji.

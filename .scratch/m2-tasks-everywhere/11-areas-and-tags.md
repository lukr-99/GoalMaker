# M2-11: Areas and tags

**Status:** done 2026-09-19, except custom colors (the owner's decision) · **Milestone:** M2

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

- Archiving (migration 0006, `areas.archived_at`): an archived area keeps its tasks and chips but
  leaves the pickers and filters; the managers list it under Archived with Restore, and naming it
  in the composer brings it back. Checked on the emulator: archived on the phone, synced, the task
  kept its chip and the filter row went away.

## Left
- **Custom colors** wait for the owner's decision. `areas.color` holds a palette id so every theme
  can draw it for light and dark and keep WCAG AA contrast (migration 0003, ADR 0008); a free hex
  color would undo that. Options: more palette entries (no migration), or a custom hex that each
  theme adjusts for contrast (a migration, a contract rule, and both renderers).
- Windows draws emoji in one color; WPF has no color emoji.

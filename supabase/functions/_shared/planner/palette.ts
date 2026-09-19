/**
 * The area palette's color ids in order (contracts/design/themes.json, areaPalette), for areas the
 * connector creates. A test keeps this list equal to the design tokens.
 */
export const AREA_COLORS = [
  "violet",
  "blue",
  "cyan",
  "teal",
  "green",
  "lime",
  "yellow",
  "orange",
  "red",
  "pink",
  "magenta",
  "slate",
  "indigo",
  "sky",
  "emerald",
  "amber",
  "coral",
  "rose",
  "purple",
  "stone",
];

/** The color a new area gets: the first one no area uses yet, then around the palette again (as the apps do). */
export function colorForNewArea(used: string[], areaCount: number): string {
  return AREA_COLORS.find((color) => !used.includes(color)) ?? AREA_COLORS[areaCount % AREA_COLORS.length];
}

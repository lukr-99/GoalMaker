import { assertEquals } from "jsr:@std/assert@1.0.13";
import { AREA_COLORS, colorForNewArea } from "./palette.ts";

Deno.test("the connector's area colors are the design tokens' palette, in order", async () => {
  const themes = JSON.parse(
    await Deno.readTextFile(new URL("../../../../contracts/design/themes.json", import.meta.url)),
  );
  assertEquals(AREA_COLORS, themes.areaPalette.colors.map((color: { id: string }) => color.id));
});

Deno.test("a new area takes the first unused color, then goes around again", () => {
  assertEquals(colorForNewArea([], 0), AREA_COLORS[0]);
  assertEquals(colorForNewArea([AREA_COLORS[0], AREA_COLORS[2]], 2), AREA_COLORS[1]);
  assertEquals(colorForNewArea(AREA_COLORS, AREA_COLORS.length + 1), AREA_COLORS[1]);
});

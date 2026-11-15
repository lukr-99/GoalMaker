# About

Settings > About opens a page of its own on both apps: what GoalMaker is, who made it and how to
reach them, what the app keeps and where, this build's facts and the credits.

## What it holds

1. **The hero**, on a hero card like the big-number cards: the mark drawing its arrow, the name in the
   theme's heading face, one line on what GoalMaker is, and the version as the big number in the
   hero's accent. A trend line climbs behind it. A dev build adds a small "Dev build" tag.
2. **The author card**, the centrepiece: a card ringed from the primary to the accent color, a badge
   with the author's initials in the big-number face, "Made by" and the name, the feedback invitation,
   and three link buttons: Instagram and GitHub filled with the primary color, and "Report a bug or an
   idea" (the repository's Issues page) outlined. The links open in the browser.
3. **Your data**: what the code really does. Everything is kept on the device first and synced to the
   backend in the owner's account, row security keeps it to that account, Tally keeps its raw record on
   the device and sends only daily minutes ([tally](tally.md)), the quick chat asks Gemini through the
   backend ([assistant](assistant.md)), Claude needs a connector link ([connector](connector.md)), and
   a backup is a file the owner saves ([backup](backup.md)). A local only dev build says that nothing
   leaves the device instead.
4. **This build**: version, kind (release or dev build) and the backend address, selectable.
5. **Credits**: GoalMaker's own licence, the four fonts (SIL OFL 1.1), the icons and the notable
   libraries each app ships, with their licences.

## Where things live

- The author's name and links are written once per app: `domain/about/AuthorLinks.kt` on the phone
  and `GoalMaker.Core/About/AuthorLinks.cs` on the PC. `contracts/vectors/about.json` holds the same
  values and the monogram rule, and both apps' tests read it, so the two cannot drift.
- The credits are per app, because the apps ship different libraries: `ui/about/AboutCredits.kt` and
  `AboutViewModel.Credits`. Add or remove a credit when a dependency comes or goes. Both apps' tests
  check that every font folder in `fonts/` is credited.

## Look and motion

The page uses the theme's roles only (hero, primary, accent, surface), so each theme gives it its
own look. Each block fades in and rises 18 dp a beat after the one above (standard duration, 70 ms
apart), and the trend lines draw themselves. With reduce motion everything is simply in place.

On Windows a fully round theme (Sunrise) has a corner of 999, which WPF draws as an ellipse, so the
page uses `GM.ChipCorner` for its tags and `GM.LinkButtonCorner` (at most 26) for its link buttons.

## Seeing it without a device

- Windows: `dotnet test --project tests/GoalMaker.App.Tests -- --explicit only` with the
  `PageSnapshots.About` test writes `about-<theme>-<mode>.png` to `GOALMAKER_SNAPSHOTS`.
- Android: set `GOALMAKER_SNAPSHOTS` and run `AboutScreenTest`; it writes
  `android-about-<theme>-<mode>.png` there.

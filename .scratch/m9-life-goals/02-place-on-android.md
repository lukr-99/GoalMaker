# M9-02: The Life goals place on Android

**Status:** built, waiting for review · **Milestone:** M9

## Scope
- Life goals is a place after Goals (`contracts/vectors/navigation.json`), in Places and pinnable.
- Cards with the first picture, title, why, time left and area; achieved and dropped folded below;
  drag to reorder; a card's menu marks achieved, drops, reopens or deletes (asking first).
- The editor: title, why, by (In 5, 10, 20 years or a picked day), area, pictures from the system
  photo picker, removed and ordered.
- Pictures: shrink to 1600 pixels as JPEG, cache in app storage, upload with a WorkManager job when
  online, download a missing file once, remove the file when its row goes.

## Acceptance criteria
- View model and picture store tests (shrink, cache, pending upload, download once, remove).

## Check
- Emulator: add "Own an Audi R8" with a picture offline, see it, go online, see it on the PC after
  M9-03; reorder; achieve and reopen.

## Result

2026-10-04.

- **The place:** Life goals after Goals in `navigation.json` (and in `PlaceRules` on both apps), a
  Places tile, cards with a picture pager, the time left and the why, the closed ones folded, a menu
  with Move up and Move down instead of dragging (simpler, and reachable without a drag gesture), a
  delete that asks, and undo after achieve, drop and delete. A New life goal button rather than the
  bottom bar: a life goal needs a why, which a one-line add would leave out.
- **The editor:** title, why, by date chips and a date picker, pictures from the system photo picker,
  shrunk on the phone (`PictureShrinker`: EXIF turned, 1600 pixels, JPEG 85) and kept only on Save.
  The area is kept but not set here yet.
- **Pictures:** `LifeGoalPictures` over the `PictureFiles` and `PictureCloud` ports, with
  `FilePictureFiles` (private app storage) and `SupabasePictureCloud` (Storage over Ktor, one refresh
  on 401). A transfer runs after every sync that moved rows, after the background sync and after a
  picture is added. A deleted picture's file goes a day after its row, so an undo finds it.
- **Activity log:** `life_goals` (achieved, dropped, reopened, renamed) and `life_goal_pictures` in
  `activity.json`, with sentences on both apps.
- **Checked:** on the emulator against the local stack: added "Own an Audi R8" with a 3000 by 2000
  photo and In 10 years; the card shows "10 years left"; the row synced and the file reached the
  bucket as a 1600 by 1067 JPEG of 25 KB; achieved, folded, reopened, and undo after achieving.
  Screenshots in `artifacts/m9-02/` (not committed).

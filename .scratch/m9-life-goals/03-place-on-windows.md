# M9-03: The Life goals page on Windows

**Status:** built, waiting for review · **Milestone:** M9

## Scope
- Life goals in the sidebar after Goals, with the same cards, folds, menu and editor as the phone.
  All copy in `Strings.xaml`.
- Pictures from a file picker or dropped on the editor, shrunk to 1600 pixels as JPEG, cached under
  the app's local data, uploaded when online, downloaded once when missing.

## Acceptance criteria
- View model and picture store tests, like Android's.

## Check
- A picture added on the phone shows on the PC and the other way round; keyboard reach on the cards.

## Result

2026-10-04, built on the same branch as M9-02, since the place list in `navigation.json` is one
contract both apps read.

- **Core:** `IPictureFiles`, `IPictureCloud`, `ShrunkPicture` and `LifeGoalPictures`, the same rules
  as Kotlin, with the Kotlin tests ported plus an orphan file and a signed-out case.
- **Infrastructure:** `FilePictureFiles` (a `pictures-<backend>` folder beside the replica, or
  `pictures-local`), `SupabasePictureCloud` and `PictureShrinker` (WPF imaging, EXIF orientation, 1600
  pixels, JPEG 85), each tested.
- **App:** `LifeGoalsPage` with its view models, the sidebar entry after Goals, `life-goals` as a
  start-up place, a Places tile, the editor overlay with a file picker and dropped files, the delete
  question, undo, and keyboard reach (`KeyboardReachTests`).
- **Changed from Android:** a 401 is not retried inside the call. Like the PostgREST client on
  Windows it counts as offline and asks for a sync, which renews the session, and the transfer after
  that sync tries again.
- **Checked:** the page and editor snapshots (`PageSnapshots.LifeGoalsPage_`) and the full test
  suite. A picture moving between the phone and the PC was not checked by hand.

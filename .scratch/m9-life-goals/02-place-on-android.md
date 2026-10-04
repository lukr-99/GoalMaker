# M9-02: The Life goals place on Android

**Status:** planned · **Milestone:** M9

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

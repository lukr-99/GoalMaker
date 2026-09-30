# M7-02: The chat on Android

**Status:** todo · **Milestone:** M7

## Scope
- A switch in the composer between quick-add (as today: the line is parsed and saved at once) and
  chat (the owner's board asks for it: "a toggle for the AI powered chat vs simple task adding").
  The composer remembers the last choice on the device.
- In chat, the message goes to `assistant` with the owner's session; the answer shows above the
  composer as a short thread (the owner's lines and the answers), with a thinking indicator while it
  runs. Changes it made arrive through sync like any other and show in the lists at once.
- Offline, or signed out, or with no key on the server: the switch says chat isn't available and
  why, and quick-add keeps working.
- The thread lives only in memory and a clear button empties it.

## Acceptance criteria
- View-model tests with a fake assistant client: the switch and its memory, a request and its
  answer, an error, offline.
- The composer's quick-add behaves exactly as before with the switch on quick-add.

## Check
- Emulator against the local stack with a real key: add a task and ask about today in chat; the
  task appears in Today.
